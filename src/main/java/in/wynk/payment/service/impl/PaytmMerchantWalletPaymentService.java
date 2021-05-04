package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.paytm.pg.merchant.CheckSumServiceHelper;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.WynkResponse;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.Status;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.common.utils.Utils;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.StatusMode;
import in.wynk.payment.core.dao.entity.Key;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.entity.Wallet;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.paytm.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.request.WalletAddMoneyRequest;
import in.wynk.payment.dto.request.WalletLinkRequest;
import in.wynk.payment.dto.request.WalletValidateLinkRequest;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.dto.response.paytm.*;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.*;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.logging.BaseLoggingMarkers.APPLICATION_ERROR;
import static in.wynk.logging.BaseLoggingMarkers.HTTP_ERROR;
import static in.wynk.payment.core.constant.PaymentCode.PAYTM_WALLET;
import static in.wynk.payment.core.constant.PaymentConstants.WALLET;
import static in.wynk.payment.core.constant.PaymentConstants.WALLET_USER_ID;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYTM_ERROR;
import static in.wynk.payment.dto.paytm.PayTmConstants.*;
import static in.wynk.payment.dto.paytm.PayTmConstants.PAYTM_CHECKSUMHASH;

@Slf4j
@Service(BeanConstant.PAYTM_MERCHANT_WALLET_SERVICE)
public class PaytmMerchantWalletPaymentService implements IRenewalMerchantWalletService, IUserPreferredPaymentService, IMerchantPaymentRefundService {

    @Value("${paytm.native.merchantId}")
    private String MID;

    @Value("${paytm.native.secret}")
    private String SECRET;

    @Value("${paytm.sendOtp.api}")
    private String SEND_OTP;

    @Value("${paytm.native.clientId}")
    private String CLIENT_ID;

    @Value("${paytm.autoDebit.api}")
    private String AUTO_DEBIT;

    @Value("${payment.success.page}")
    private String successPage;

    @Value("${payment.failure.page}")
    private String failurePage;

    @Value("${paytm.native.wcf.callbackUrl}")
    private String callBackUrl;

    @Value("${paytm.validateOtp.api}")
    private String VALIDATE_OTP;

    @Value("${paytm.native.merchantKey}")
    private String MERCHANT_KEY;

    @Value("${paytm.refreshToken.api}")
    private String REFRESH_TOKEN;

    @Value("${paytm.validateToken.api}")
    private String VALIDATE_TOKEN;

    @Value("${paytm.fetchInstrument.api}")
    private String FETCH_INSTRUMENT;

    @Value("${paytm.transactionStatus.api}")
    private String TRANSACTION_STATUS;

    @Value("${paytm.revokeAccessToken.api}")
    private String REVOKE_ACCESS_TOKEN;

    @Value("{payment.encryption.key}")
    private String paymentEncryptionKey;

    @Value("${paytm.requesting.website}")
    private String paytmRequestingWebsite;

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final IUserPaymentsManager userPaymentsManager;
    private final CheckSumServiceHelper checkSumServiceHelper;
    private final PaymentCachingService paymentCachingService;
    private final ApplicationEventPublisher applicationEventPublisher;

    public PaytmMerchantWalletPaymentService(ObjectMapper objectMapper, @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, IUserPaymentsManager userPaymentsManager, PaymentCachingService paymentCachingService, ApplicationEventPublisher applicationEventPublisher) {
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.userPaymentsManager = userPaymentsManager;
        this.paymentCachingService = paymentCachingService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.checkSumServiceHelper = CheckSumServiceHelper.getCheckSumServiceHelper();
    }

    @Override//TODO refactor and test
    public BaseResponse<?> handleCallback(CallbackRequest callbackRequest) {
        Transaction transaction = TransactionContext.get();
        final int planId = transaction.getPlanId();
        Map<String, List<String>> params = (Map<String, List<String>>) callbackRequest.getBody();
        List<String> status = params.getOrDefault(PAYTM_STATUS, new ArrayList<>());
        if (CollectionUtils.isEmpty(status) || !status.get(0).equalsIgnoreCase(PAYTM_STATUS_SUCCESS)) {
            log.error(APPLICATION_ERROR, "Add money txn at paytm failed");
            throw new RuntimeException("Failed to add money to wallet");
        }
        log.info("Successfully added money to wallet. Now withdrawing amount");
        return doCharging(ChargingRequest.builder().planId(planId).paymentCode(PAYTM_WALLET).build());
    }

    @Override//TODO refactor and test
    public BaseResponse<?> doCharging(ChargingRequest chargingRequest) {
        Transaction transaction = TransactionContext.get();
        final String uid = transaction.getUid();
        final String msisdn = transaction.getMsisdn();
        PaytmWalletDetails paytmWalletDetails = this.getUserPreferredPayments(uid, transaction.getPlanId().toString(), "");

        final PlanDTO selectedPlan = paymentCachingService.getPlan(chargingRequest.getPlanId());

        final PaymentEvent eventType = chargingRequest.isAutoRenew() ? PaymentEvent.SUBSCRIBE : PaymentEvent.PURCHASE;

//        if (!paytmWalletDetails.isFundsSufficient()) {
//            throw new WynkRuntimeException(WynkErrorType.UT001);
//        }
        if (StringUtils.isBlank(msisdn) || StringUtils.isBlank(uid)) {
            throw new WynkRuntimeException(WynkErrorType.UT001, "Linked Msisdn or UID not found for user");
        }
        try {
            withdrawAndUpdateTransactionFromSource(transaction);
            return BaseResponse.redirectResponse(String.format(successPage, SessionContextHolder.get().getId().toString(), transaction.getId().toString()));
        } catch (Exception e) {
            return BaseResponse.redirectResponse(failurePage);
        } finally {
            //Add reconciliation
//            PaymentReconciliationMessage message = new PaymentReconciliationMessage(transaction);
//            publishSQSMessage(reconciliationQueue, reconciliationMessageDelay, message);
        }
    }
    //TODO refactor and test
    private void withdrawAndUpdateTransactionFromSource(Transaction transaction) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String deviceId = sessionDTO.get(DEVICE_ID);
        try {
            String accessToken = getAccessToken(transaction.getUid());
            PaytmChargingResponse paytmChargingResponse = withdrawFromPaytm(transaction.getUid(), transaction, String.valueOf(transaction.getAmount()), accessToken, deviceId);
            if (paytmChargingResponse != null && paytmChargingResponse.getStatus().equalsIgnoreCase(PAYTM_STATUS_SUCCESS)) {
                transaction.setStatus(TransactionStatus.SUCCESS.name());
            } else if (Objects.nonNull(paytmChargingResponse)) {
                transaction.setStatus(TransactionStatus.FAILURE.name());
                applicationEventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(paytmChargingResponse.getResponseCode()).description(paytmChargingResponse.getResponseMessage()).build());
            }
        } catch (WynkRuntimeException e) {
            throw e;
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.name());
            log.error(PAYTM_ERROR, "unable to execute withdrawAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException("Something went wrong in withdrawAndUpdateTransactionFromSource due to ", e);
        }
    }

    @Override//TODO refactor and test
    public BaseResponse<?> refund(AbstractPaymentRefundRequest request) {
        return null;
    }

    @Override//TODO refactor and test
    public void doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        //TODO: since paytm access token is of 30 days validity, we need to integrate with paytm subscription system for renewal
        String uid = paymentRenewalChargingRequest.getUid();
        String msisdn = paymentRenewalChargingRequest.getMsisdn();
        Integer planId = paymentRenewalChargingRequest.getPlanId();
        final PlanDTO selectedPlan = paymentCachingService.getPlan(planId);
        double amount = selectedPlan.getFinalPrice();
        String accessToken = getAccessToken(uid);
        String deviceId = "abcd"; //TODO: might need to store device id or can be fetched from merchant txn.

        final PaymentEvent eventType = selectedPlan.getPlanType() == PlanType.ONE_TIME_SUBSCRIPTION ? PaymentEvent.PURCHASE : PaymentEvent.SUBSCRIBE;

        Transaction transaction = TransactionContext.get();
        PaytmChargingResponse response = withdrawFromPaytm(uid, transaction, String.valueOf(amount), accessToken, deviceId);
    }

    private String getAccessToken(String uid) {
        UserPreferredPayment userPreferredPayment = userPaymentsManager.getPaymentDetails(getKey(uid));
        if (Objects.nonNull(userPreferredPayment)) {
            Wallet wallet = (Wallet) userPreferredPayment;
            String accessToken = wallet.getAccessToken();
            if (StringUtils.isBlank(accessToken) || wallet.getTokenValidity() < System.currentTimeMillis()) {
                throw new WynkRuntimeException("token expired error");
            }
            return accessToken;
        } else {
            throw new WynkRuntimeException("link wallet error");
        }
    }
    //TODO refactor and test
    private PaytmChargingResponse withdrawFromPaytm(String uid, Transaction transaction, String amount, String accessToken, String deviceId) {
        MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            URI uri = new URIBuilder(AUTO_DEBIT).build();
            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put("MID", MID);
            parameters.put("ReqType", "WITHDRAW");
            parameters.put("TxnAmount", String.valueOf(amount));
            parameters.put("AppIP", "capi-host");
            parameters.put("OrderId", transaction.getId().toString());
            parameters.put("Currency", "INR");
            parameters.put("DeviceId", deviceId);
            parameters.put("SSOToken", accessToken);
            parameters.put("PaymentMode", "PPI");
            parameters.put("CustId", uid);
            parameters.put("IndustryType", "Retail");
            parameters.put("Channel", "WEB");
            parameters.put("AuthMode", "USRPWD");
            String checkSum = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, parameters);
            parameters.put("CheckSum", checkSum);
            log.info("Generated checksum: {} for payload: {}", checkSum, parameters);
            merchantTransactionEventBuilder.request(parameters);
            RequestEntity<Map<String, String>> requestEntity = new RequestEntity<>(parameters, HttpMethod.POST, uri);
            log.info("Paytm wallet charging request: {}", requestEntity);
            ResponseEntity<PaytmChargingResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmChargingResponse.class);
            log.info("Paytm wallet charging response: {}", responseEntity);
            merchantTransactionEventBuilder.externalTransactionId(responseEntity.getBody().getOrderId()).response(responseEntity.getBody());
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            log.error(PAYTM_ERROR, "Error from paytm : {}", e.getResponseBodyAsString(), e);
            throw new WynkRuntimeException(PAYTM_ERROR, e.getResponseBodyAsString(), e);
        } catch (Exception ex) {
            log.error(PAYTM_ERROR, "Error from paytm : {}", ex.getMessage(), ex);
            throw new WynkRuntimeException(PAYTM_ERROR, ex.getMessage(), ex);
        } finally {
            applicationEventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    @Override//TODO refactor and test
    public BaseResponse<?> status(AbstractTransactionStatusRequest transactionStatusRequest) {
        final Transaction transaction = TransactionContext.get();
        if (transactionStatusRequest.getMode().equals(StatusMode.SOURCE)) {
            PaytmChargingStatusResponse paytmResponse = fetchChargingStatusFromPaytm(transaction.getId().toString());
            if (paytmResponse != null && paytmResponse.getStatus().equalsIgnoreCase(PAYTM_STATUS_SUCCESS)) {
                return BaseResponse.<ChargingStatusResponse>builder().body(ChargingStatusResponse.success(transaction.getId().toString(), paymentCachingService.validTillDate(transaction.getPlanId()), transaction.getPlanId())).status(HttpStatus.OK).build();
            }
        } else if (transactionStatusRequest.getMode().equals(StatusMode.LOCAL)) {
            if (TransactionStatus.SUCCESS.equals(transaction.getStatus())) {
                return BaseResponse.<ChargingStatusResponse>builder().body(ChargingStatusResponse.success(transaction.getId().toString(), paymentCachingService.validTillDate(transactionStatusRequest.getPlanId()), transactionStatusRequest.getPlanId())).status(HttpStatus.OK).build();
            }
        }
        return BaseResponse.<ChargingStatusResponse>builder().body(ChargingStatusResponse.failure(transaction.getId().toString(), transaction.getPlanId())).status(HttpStatus.OK).build();
    }
    //TODO refactor and test
    private PaytmChargingStatusResponse fetchChargingStatusFromPaytm(String txnId) {
        try {
            URI uri = new URIBuilder(TRANSACTION_STATUS).build();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            PaytmStatusRequest.PaytmStatusRequestBody body = PaytmStatusRequest.PaytmStatusRequestBody.builder().mid(MID).orderId(txnId).txnType("PREAUTH").build();
            String jsonPayload = objectMapper.writeValueAsString(body);
            String signature = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, jsonPayload);
            log.info("Generated checksum: {} for payload: {}", signature, jsonPayload);
            PaytmRequestHead paytmRequestHead = PaytmRequestHead.builder().clientId(CLIENT_ID).version("v1").requestTimestamp(System.currentTimeMillis() + "").signature(signature).channelId("WEB").build();
            RequestEntity<PaytmStatusRequest> requestEntity = new RequestEntity<>(PaytmStatusRequest.builder().body(body).head(paytmRequestHead).build(), headers, HttpMethod.POST, uri);
            log.info("Paytm wallet charging status request: {}", requestEntity);
            ResponseEntity<PaytmChargingStatusResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmChargingStatusResponse.class);
            log.info("Paytm wallet charging status response: {}", responseEntity);
            return responseEntity.getBody();
        } catch (URISyntaxException e) {
            throw new WynkRuntimeException("HttpStatusCode Exception occurred");
        } catch (Exception e) {
            throw new WynkRuntimeException("Exception occurred");
        }
    }

    @Override
    public BaseResponse<?> linkRequest(WalletLinkRequest walletLinkRequest) {
        try {
            String phone = walletLinkRequest.getEncSi();
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            sessionDTO.put(WALLET_USER_ID, phone);
            log.info("Sending OTP to {} via PayTM", phone);
            URI uri = new URIBuilder(SEND_OTP).build();
            HttpHeaders headers = getHttpHeaders();
            PaytmWalletOtpRequest paytmWalletOtpRequest = PaytmWalletOtpRequest.builder().phone(phone).scopes(Arrays.asList("wallet")).build();
            RequestEntity<PaytmWalletOtpRequest> requestEntity = new RequestEntity<>(paytmWalletOtpRequest, headers, HttpMethod.POST, uri);
            log.info("Paytm OTP request: {}", requestEntity);
            ResponseEntity<PaytmWalletLinkResponsePaytm> responseEntity = restTemplate.exchange(requestEntity, PaytmWalletLinkResponsePaytm.class);
            log.info("Paytm OTP response: {}", responseEntity);
            HttpStatus statusCode = responseEntity.getStatusCode();
            PaytmWalletLinkResponsePaytm paytmWalletLinkResponse = responseEntity.getBody();
            if (!statusCode.is2xxSuccessful() || paytmWalletLinkResponse.getStatus() == Status.FAILURE) {
                String responseCode = paytmWalletLinkResponse.getResponseCode();
                PayTmErrorCodes errorCode = PayTmErrorCodes.resolveErrorCode(responseCode);
                log.error(HTTP_ERROR, "Error in sending otp. Reason: [{}]", errorCode.getMessage());
                throw new RuntimeException("Error in sending otp.");
            }
            if (StringUtils.isBlank(paytmWalletLinkResponse.getState_token())) {
                throw new RuntimeException("Paytm responded with empty state for OTP response");
            }
            log.info("Otp sent successfully. Status: {}", paytmWalletLinkResponse.getStatus());
            sessionDTO.put(STATE_TOKEN, paytmWalletLinkResponse.getState_token());
            WynkResponse.WynkResponseWrapper<PaytmWalletLinkResponsePaytm> response = WynkResponse.WynkResponseWrapper.<PaytmWalletLinkResponsePaytm>builder().data(paytmWalletLinkResponse).build();
            return BaseResponse.<WynkResponse.WynkResponseWrapper>builder().status(HttpStatus.OK).body(response).headers(requestEntity.getHeaders()).build();
        } catch (URISyntaxException e) {
            log.error(PAYTM_ERROR, "Error from paytm: {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, "Paytm error - " + e.getMessage());
        }
    }

    @Override
    public BaseResponse<?> validateLink(WalletValidateLinkRequest walletValidateLinkRequest) {
        try {
            URI uri = new URIBuilder(VALIDATE_OTP).build();
            HttpHeaders headers = getHttpHeaders();
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            walletValidateLinkRequest.setState_token(sessionDTO.get(STATE_TOKEN));
            RequestEntity<WalletValidateLinkRequest> requestEntity = new RequestEntity<>(walletValidateLinkRequest, headers, HttpMethod.POST, uri);
            log.info("Validate paytm otp request: {}", requestEntity);
            ResponseEntity<PaytmWalletValidateLinkResponsePaytm> responseEntity = restTemplate.exchange(requestEntity, PaytmWalletValidateLinkResponsePaytm.class);
            PaytmWalletValidateLinkResponsePaytm paytmWalletValidateLinkResponse = responseEntity.getBody();
            if (paytmWalletValidateLinkResponse != null && paytmWalletValidateLinkResponse.getStatus().equals(Status.SUCCESS)) {
                saveToken(paytmWalletValidateLinkResponse);
                WynkResponse.WynkResponseWrapper<Void> response = WynkResponse.WynkResponseWrapper.<Void>builder().data(null).build();
                return BaseResponse.<WynkResponse.WynkResponseWrapper>builder().status(HttpStatus.OK).body(response).headers(requestEntity.getHeaders()).build();
            }
        } catch (URISyntaxException e) {
            AnalyticService.update("otpValidated", false);
            log.error(PAYTM_ERROR, "Error in response: {}", e.getMessage(), e);
        }
        throw new WynkRuntimeException(PaymentErrorType.PAY998, PAYTM_ERROR);
    }

    private HttpHeaders getHttpHeaders() {
        String authHeader = String.format("Basic %s", Utils.encodeBase64(CLIENT_ID + ":" + SECRET));
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", authHeader);
        headers.add("Content-Type", "application/json");
        try {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            headers.add("x-device-identifier", sessionDTO.get(DEVICE_ID));
        } finally {
            return headers;
        }
    }

    private void saveToken(PaytmWalletValidateLinkResponsePaytm tokenResponse) {
        if (!tokenResponse.getTokens().isEmpty()) {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String walletUserId = sessionDTO.get(WALLET_USER_ID);
            String uid = sessionDTO.get(UID);
            Wallet wallet = Wallet.builder()
                    .id(getKey(uid))
                    .walletUserId(walletUserId)
                    .tokenValidity(tokenResponse.getExpiry())
                    .accessToken(tokenResponse.getAccessToken())
                    .refreshToken(tokenResponse.getRefreshToken())
                    .build();
            userPaymentsManager.savePaymentDetails(wallet);
        }
    }

    @Override
    public BaseResponse<?> unlink() {
        try {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String uid = sessionDTO.get(UID);
            URI uri = new URIBuilder(REVOKE_ACCESS_TOKEN).build();
            HttpHeaders headers = getHttpHeaders();
            headers.add(PAYTM_SESSION_TOKEN, getAccessToken(uid));
            RequestEntity requestEntity = new RequestEntity(headers, HttpMethod.DELETE, uri);
            ResponseEntity responseEntity = restTemplate.exchange(requestEntity, String.class);
            HttpStatus statusCode = responseEntity.getStatusCode();
            if (statusCode.is2xxSuccessful()) {
                userPaymentsManager.deletePaymentDetails(userPaymentsManager.getPaymentDetails(getKey(uid)));
                WynkResponse.WynkResponseWrapper<Void> response = WynkResponse.WynkResponseWrapper.<Void>builder().data(null).build();
                return BaseResponse.<WynkResponse.WynkResponseWrapper>builder().status(HttpStatus.OK).body(response).headers(requestEntity.getHeaders()).build();
            }
        } catch (Exception e) {}
        return BaseResponse.status(false);
    }

    @Override
    public BaseResponse<PaytmWalletDetails> balance(String uid, String planId, String deviceId) {
        PlanDTO planDTO = paymentCachingService.getPlan(planId);
        UserPreferredPayment userPreferredPayment = userPaymentsManager.getPaymentDetails(getKey(uid));
        if (Objects.nonNull(userPreferredPayment)) {
            Wallet wallet = (Wallet) userPreferredPayment;
            String accessToken = wallet.getAccessToken();
            if (validateAccessToken(uid, wallet) || refreshAccessToken(uid, wallet, deviceId)) {
                try {
                    URI uri = new URIBuilder(FETCH_INSTRUMENT).build();
                    PaytmConsultBalanceRequest.ConsultBalanceRequestBody body = PaytmConsultBalanceRequest.ConsultBalanceRequestBody.builder().userToken(accessToken).mid(MID).txnAmount(planDTO.getFinalPrice()).build();
                    String jsonPayload = objectMapper.writeValueAsString(body);
                    log.debug("Generating signature for payload: {}", jsonPayload);
                    String signature = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, jsonPayload);
                    PaytmRequestHead head = PaytmRequestHead.builder().clientId(CLIENT_ID).version("v1").requestTimestamp(System.currentTimeMillis() + "").signature(signature).channelId("WEB").build();
                    PaytmConsultBalanceRequest paytmConsultBalanceRequest = PaytmConsultBalanceRequest.builder().head(head).body(body).build();
                    RequestEntity<PaytmConsultBalanceRequest> requestEntity = new RequestEntity<>(paytmConsultBalanceRequest, HttpMethod.POST, uri);
                    log.info("Paytm wallet balance request: {}", requestEntity);
                    ResponseEntity<PaytmConsultBalanceResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmConsultBalanceResponse.class);
                    log.info("Paytm wallet balance response: {}", responseEntity);
                    AnalyticService.update("PAYTM_RESPONSE_CODE", responseEntity.getStatusCodeValue());
                    PaytmConsultBalanceResponse payTmResponse = responseEntity.getBody();
                    if (payTmResponse != null && payTmResponse.getBody() != null && payTmResponse.getResultStatus().equals(Status.SUCCESS.toString())) {
                        PaytmWalletDetails response = PaytmWalletDetails.builder()
                                .active(true)
                                .linked(true)
                                .linkedMobileNo(wallet.getWalletUserId())
                                .balance(payTmResponse.getPayOption().getAmount())
                                .walletCode(payTmResponse.getPayOption().getPayMethod())
                                .displayName(payTmResponse.getPayOption().getDisplayName())
                                .expiredAmount(payTmResponse.getPayOption().getExpiredAmount())
                                .fundSufficient(payTmResponse.getPayOption().isFundSufficient())
                                .deficitBalance(payTmResponse.getPayOption().getDeficitAmount())
                                .addMoneyAllowed(payTmResponse.getPayOption().isAddMoneyAllowed())
                                .build();
                        return BaseResponse.<PaytmWalletDetails>builder().body(response).status(HttpStatus.OK).build();
                    }
                } catch (URISyntaxException e) {
                    throw new RuntimeException("Http Status Exception Occurred");
                } catch (Exception e) {
                    throw new RuntimeException("Unknown Exception Occurred");
                }
            }
        }
        return BaseResponse.<PaytmWalletDetails>builder().body(PaytmWalletDetails.builder().active(false).build()).status(HttpStatus.OK).build();
    }

    private boolean validateAccessToken(String uid, Wallet wallet) {
        String accessToken = wallet.getAccessToken();
        String msisdn = wallet.getWalletUserId();
        log.info("Validating access token for msisdn: {}, uid: {} with PayTM", msisdn, uid);
        if (StringUtils.isNotBlank(accessToken) && wallet.getTokenValidity() > System.currentTimeMillis()) {
            try {
                URI uri = new URIBuilder(VALIDATE_TOKEN).build();
                HttpHeaders headers = new HttpHeaders();
                headers.add(PAYTM_SESSION_TOKEN, accessToken);
                RequestEntity requestEntity = new RequestEntity(headers, HttpMethod.GET, uri);
                log.info("Validate paytm access token request: {}", requestEntity);
                ResponseEntity<PaytmValidateTokenResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmValidateTokenResponse.class);
                PaytmValidateTokenResponse paytmValidateTokenResponse = responseEntity.getBody();
                if (paytmValidateTokenResponse != null) {
                    return paytmValidateTokenResponse.getExpires() > System.currentTimeMillis();
                }
            } catch (URISyntaxException e) {
                log.error(PAYTM_ERROR, "Error from paytm: {} , response: {}", e.getMessage(), e);
                throw new RuntimeException("Http Status Exception Occurred");
            }
        }
        return false;
    }

    private boolean refreshAccessToken(String uid, Wallet wallet, String deviceId) {
        try {
            String refreshToken = wallet.getRefreshToken();
            String msisdn = wallet.getWalletUserId();
            log.info("Validating access token for msisdn: {}, uid: {} with PayTM", msisdn, uid);
            URI uri = new URIBuilder(REFRESH_TOKEN).build();
            HttpHeaders headers = getHttpHeaders();
            headers.add("x-device-identifier", deviceId);
            PaytmRefreshTokenRequest paytmRefreshTokenRequest = PaytmRefreshTokenRequest.builder().deviceId(headers.getFirst("x-device-identifier")).grantType("refresh_token").refreshToken(refreshToken).build();
            RequestEntity<PaytmRefreshTokenRequest> requestEntity = new RequestEntity<>(paytmRefreshTokenRequest, headers, HttpMethod.POST, uri);
            log.info("Validate paytm access token request: {}", requestEntity);
            ResponseEntity<PaytmRefreshTokenResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmRefreshTokenResponse.class);
            PaytmRefreshTokenResponse paytmRefreshTokenResponse = responseEntity.getBody();
            if (responseEntity.getStatusCode().is2xxSuccessful() && Objects.nonNull(paytmRefreshTokenResponse)) {
                userPaymentsManager.savePaymentDetails(Wallet.builder()
                        .id(wallet.getId())
                        .walletUserId(wallet.getWalletUserId())
                        .refreshToken(wallet.getRefreshToken())
                        .accessToken(paytmRefreshTokenResponse.getAccessToken())
                        .tokenValidity(paytmRefreshTokenResponse.getExpiresIn())
                        .build());
                return true;
            }
        } catch (URISyntaxException e) {
            log.error(PAYTM_ERROR, "Error from paytm: {} , response: {}", e.getMessage(), e);
            throw new RuntimeException("Http Status Exception Occurred");
        }
        return false;
    }

    @Override
    public BaseResponse<?> addMoney(WalletAddMoneyRequest walletAddMoneyRequest) {
        try {
            Transaction transaction = TransactionContext.get();
            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put(PAYTM_REQUEST_TYPE, ADD_MONEY);
            parameters.put(PAYTM_MID, MID);
            parameters.put(PAYTM_REQUST_ORDER_ID, transaction.getIdStr());
            parameters.put(PAYTM_REQUEST_CUST_ID, transaction.getUid());
            parameters.put(PAYTM_REQUEST_TXN_AMOUNT, String.valueOf(walletAddMoneyRequest.getAmountToCredit()));
            parameters.put(PAYTM_CHANNEL_ID, PAYTM_WEB);
            parameters.put(PAYTM_INDUSTRY_TYPE_ID, RETAIL);
            parameters.put(PAYTM_REQUESTING_WEBSITE, paytmRequestingWebsite);
            parameters.put(PAYTM_SSO_TOKEN, getAccessToken(transaction.getUid()));
            parameters.put(PAYTM_REQUEST_CALLBACK, callBackUrl+SessionContextHolder.getId());
            parameters.put(PAYTM_CHECKSUMHASH, checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, parameters));
            String payTmRequestParams = objectMapper.writeValueAsString(parameters);
            payTmRequestParams = EncryptionUtils.encrypt(payTmRequestParams, paymentEncryptionKey);
            Map<String, String> params = new HashMap<>();
            params.put(INFO, payTmRequestParams);
            WynkResponse.WynkResponseWrapper<Map<String, String>> response = WynkResponse.WynkResponseWrapper.<Map<String, String>>builder().data(params).build();
            return BaseResponse.<WynkResponse.WynkResponseWrapper>builder().status(HttpStatus.OK).body(response).build();
        } catch (Exception e) {
            log.error(e.toString());
            throw new WynkRuntimeException(e);
        }
    }

    @Override
    public PaytmWalletDetails getUserPreferredPayments(String uid, String planId, String deviceId) {
        return this.balance(uid, planId, deviceId).getBody();
    }

    private Key getKey(String uid) {
        return Key.builder().uid(uid).paymentGroup(WALLET).paymentCode(PAYTM_WALLET.name()).build();
    }

}