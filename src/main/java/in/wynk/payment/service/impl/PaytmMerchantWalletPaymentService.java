package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import com.paytm.pg.merchant.CheckSumServiceHelper;
import in.wynk.common.dto.SessionDTO;
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
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.dao.entity.Wallet;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.paytm.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.dto.response.paytm.PaytmChargingResponse;
import in.wynk.payment.dto.response.paytm.PaytmChargingStatusResponse;
import in.wynk.payment.dto.response.paytm.PaytmWalletLinkResponse;
import in.wynk.payment.dto.response.paytm.PaytmWalletValidateLinkResponse;
import in.wynk.payment.service.*;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.enums.PlanType;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.net.URI;
import java.util.*;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.logging.BaseLoggingMarkers.APPLICATION_ERROR;
import static in.wynk.logging.BaseLoggingMarkers.HTTP_ERROR;
import static in.wynk.payment.core.constant.PaymentCode.PAYTM_WALLET;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYTM_ERROR;
import static in.wynk.payment.dto.paytm.PayTmConstants.*;

@Service(BeanConstant.PAYTM_MERCHANT_WALLET_SERVICE)
public class PaytmMerchantWalletPaymentService implements IRenewalMerchantWalletService, IUserPreferredPaymentService {

    private static final Logger logger = LoggerFactory.getLogger(PaytmMerchantWalletPaymentService.class);

    @Value("${paytm.native.clientId}")
    private String CLIENT_ID;

    @Value("${paytm.native.merchantKey}")
    private String MERCHANT_KEY;

    @Value("${paytm.native.merchantId}")
    private String MID;

    @Value("${paytm.native.secret}")
    private String SECRET;

    @Value("${paytm.native.accounts.baseUrl}")
    private String ACCOUNTS_URL;

    @Value("${paytm.native.services.baseUrl}")
    private String SERVICES_URL;

    @Value("${paytm.native.wcf.addMoneyUrl}")
    private String addMoneyPage;

    @Value("${paytm.native.wcf.callbackUrl}")
    private String callBackUrl;

    @Value("${paytm.requesting.website}")
    private String paytmRequestingWebsite;

    @Value("${payment.success.page}")
    private String successPage;

    @Value("${payment.failure.page}")
    private String failurePage;

    @Value("{payment.encryption.key}")
    private String paymentEncryptionKey;

    @Value("${payment.pooling.queue.reconciliation.name}")
    private String reconciliationQueue;

    @Value("${payment.pooling.queue.reconciliation.sqs.producer.delayInSecond}")
    private int reconciliationMessageDelay;

    @Autowired
    private Gson gson;

    @Autowired
    @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE)
    private RestTemplate restTemplate;

    @Autowired
    private IUserPaymentsManager userPaymentsManager;
    @Autowired
    private ITransactionManagerService transactionManager;
    @Autowired
    private ISQSMessagePublisher messagePublisher;
    @Autowired
    private PaymentCachingService cachingService;

    @Autowired
    private ISubscriptionServiceManager subscriptionServiceManager;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private CheckSumServiceHelper checkSumServiceHelper;

    @PostConstruct
    public void init() {
        checkSumServiceHelper = CheckSumServiceHelper.getCheckSumServiceHelper();
    }

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final int planId = sessionDTO.get(PLAN_ID);
        Map<String, List<String>> params = (Map<String, List<String>>) callbackRequest.getBody();
        List<String> status = params.getOrDefault(PAYTM_STATUS, new ArrayList<>());
        if (CollectionUtils.isEmpty(status) || !status.get(0).equalsIgnoreCase(PAYTM_STATUS_SUCCESS)) {
            logger.error(APPLICATION_ERROR, "Add money txn at paytm failed");
            throw new RuntimeException("Failed to add money to wallet");
        }
        logger.info("Successfully added money to wallet. Now withdrawing amount");
        return withdrawFromWallet(ChargingRequest.builder().planId(planId).paymentCode(PAYTM_WALLET).build());
    }

    @Override
    public BaseResponse<Void> doCharging(ChargingRequest request) {
        return withdrawFromWallet(request);
    }

    private BaseResponse<Void> withdrawFromWallet(ChargingRequest request) {
        Transaction transaction;
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String uid = sessionDTO.get(UID);
        final String msisdn = sessionDTO.get(MSISDN);
        WalletBalanceResponse walletBalanceResponse = balance().getBody();

        final PlanDTO selectedPlan = cachingService.getPlan(request.getPlanId());

        final PaymentEvent eventType = request.isAutoRenew() ? PaymentEvent.SUBSCRIBE : PaymentEvent.PURCHASE;

        if (!walletBalanceResponse.isFundsSufficient()) {
            throw new WynkRuntimeException(WynkErrorType.UT001);
        }
        if (StringUtils.isBlank(msisdn) || StringUtils.isBlank(uid)) {
            throw new WynkRuntimeException(WynkErrorType.UT001, "Linked Msisdn or UID not found for user");
        }
        transaction = transactionManager.initiateTransaction(uid, msisdn, request.getPlanId(), selectedPlan.getPrice().getAmount(), PAYTM_WALLET, eventType);
        try {
            transactionManager.updateAndPublishSync(transaction, this::withdrawAndUpdateTransactionFromSource);
            return BaseResponse.redirectResponse(String.format(successPage, SessionContextHolder.get().getId().toString(), transaction.getId().toString()));
        } catch (Exception e) {
            return BaseResponse.redirectResponse(failurePage);
        } finally {
            //Add reconciliation
//            PaymentReconciliationMessage message = new PaymentReconciliationMessage(transaction);
//            publishSQSMessage(reconciliationQueue, reconciliationMessageDelay, message);
        }
    }

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
                eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(paytmChargingResponse.getResponseCode()).description(paytmChargingResponse.getResponseMessage()).build());
            }
        } catch (WynkRuntimeException e) {
            throw e;
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.name());
            logger.error(PAYTM_ERROR, "unable to execute withdrawAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException("Something went wrong in withdrawAndUpdateTransactionFromSource due to ", e);
        }
    }

    private String getAccessToken(String uid) {
        String accessToken;
        UserPreferredPayment userPreferredPayment = userPaymentsManager.getPaymentDetails(uid, PAYTM_WALLET);
        if (Objects.nonNull(userPreferredPayment)) {
            Wallet wallet = (Wallet) userPreferredPayment.getOption();
            accessToken = wallet.getAccessToken();
            if (StringUtils.isBlank(accessToken) || wallet.getTokenValidity() < System.currentTimeMillis()) {
                //throw token expired error
                throw new WynkRuntimeException(WynkErrorType.UT018);
            }
        } else {
            //throw link wallet error
            throw new WynkRuntimeException(WynkErrorType.UT022);
        }
        return accessToken;
    }

    private PaytmChargingResponse withdrawFromPaytm(String uid, Transaction transaction, String amount, String accessToken, String deviceId) {
        Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            URI uri = new URIBuilder(SERVICES_URL + "/paymentservices/HANDLER_FF/withdrawScw").build();
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
            logger.info("Generated checksum: {} for payload: {}", checkSum, parameters);
            merchantTransactionEventBuilder.request(parameters);
            RequestEntity<Map<String, String>> requestEntity = new RequestEntity<>(parameters, HttpMethod.POST, uri);
            logger.info("Paytm wallet charging request: {}", requestEntity);
            ResponseEntity<PaytmChargingResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmChargingResponse.class);
            logger.info("Paytm wallet charging response: {}", responseEntity);
            merchantTransactionEventBuilder.externalTransactionId(responseEntity.getBody().getOrderId()).response(responseEntity.getBody());
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            logger.error(PAYTM_ERROR, "Error from paytm : {}", e.getResponseBodyAsString(), e);
            throw new WynkRuntimeException(PAYTM_ERROR, e.getResponseBodyAsString(), e);
        } catch (Exception ex) {
            logger.error(PAYTM_ERROR, "Error from paytm : {}", ex.getMessage(), ex);
            throw new WynkRuntimeException(PAYTM_ERROR, ex.getMessage(), ex);
        } finally {
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    private <T> void publishSQSMessage(String queueName, int messageDelay, T message) {
        try {
            messagePublisher.publish(SendSQSMessageRequest.<T>builder()
                    .queueName(queueName)
                    .delaySeconds(messageDelay)
                    .message(message)
                    .build());
        } catch (Exception e) {
            throw new WynkRuntimeException(QueueErrorType.SQS001, e);
        }
    }

    @Override
    public void doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        //TODO: since paytm access token is of 30 days validity, we need to integrate with paytm subscription system for renewal
        String uid = paymentRenewalChargingRequest.getUid();
        String msisdn = paymentRenewalChargingRequest.getMsisdn();
        Integer planId = paymentRenewalChargingRequest.getPlanId();
        final PlanDTO selectedPlan = cachingService.getPlan(planId);
        double amount = selectedPlan.getFinalPrice();
        String accessToken = getAccessToken(uid);
        String deviceId = "abcd"; //TODO: might need to store device id or can be fetched from merchant txn.

        final PaymentEvent eventType = selectedPlan.getPlanType() == PlanType.ONE_TIME_SUBSCRIPTION ? PaymentEvent.PURCHASE : PaymentEvent.SUBSCRIBE;

        Transaction transaction = transactionManager.initiateTransaction(uid, msisdn, planId, amount, PAYTM_WALLET, eventType);
        PaytmChargingResponse response = withdrawFromPaytm(uid, transaction, String.valueOf(amount), accessToken, deviceId);
    }

    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionStatusRequest chargingStatusRequest) {
        final String tid = chargingStatusRequest.getTransactionId();
        final Transaction transaction = transactionManager.get(tid);
        if (chargingStatusRequest.getMode().equals(StatusMode.SOURCE)) {
            PaytmChargingStatusResponse paytmResponse = fetchChargingStatusFromPaytm(tid);
            if (paytmResponse != null && paytmResponse.getStatus().equalsIgnoreCase(PAYTM_STATUS_SUCCESS)) {
                return BaseResponse.<ChargingStatusResponse>builder().body(ChargingStatusResponse.success(tid, cachingService.validTillDate(transaction.getPlanId()), transaction.getPlanId())).status(HttpStatus.OK).build();
            }
        } else if (chargingStatusRequest.getMode().equals(StatusMode.LOCAL)) {
            if (TransactionStatus.SUCCESS.equals(transaction.getStatus())) {
                return BaseResponse.<ChargingStatusResponse>builder().body(ChargingStatusResponse.success(tid, cachingService.validTillDate(chargingStatusRequest.getPlanId()), chargingStatusRequest.getPlanId())).status(HttpStatus.OK).build();
            }
        }
        return BaseResponse.<ChargingStatusResponse>builder().body(ChargingStatusResponse.failure(tid, transaction.getPlanId())).status(HttpStatus.OK).build();
    }

    private PaytmChargingStatusResponse fetchChargingStatusFromPaytm(String txnId) {
        try {
            URI uri = new URIBuilder(SERVICES_URL + "/order/status").build();
            TreeMap<String, String> paytmParams = new TreeMap<>();
            paytmParams.put("MID", MID);
            paytmParams.put("ORDERID", txnId);
            String checkSum = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, paytmParams);
            logger.info("Generated checksum: {} for payload: {}", checkSum, paytmParams);
            paytmParams.put("CHECKSUMHASH", checkSum);
            RequestEntity<Map<String, String>> requestEntity = new RequestEntity<>(paytmParams, HttpMethod.POST, uri);
            logger.info("Paytm wallet charging status request: {}", requestEntity);
            ResponseEntity<PaytmChargingStatusResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmChargingStatusResponse.class);
            logger.info("Paytm wallet charging status response: {}", responseEntity);
            return responseEntity.getBody();
        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("HttpStatusCode Exception occurred");
        } catch (Exception e) {
            throw new RuntimeException("Exception occurred");
        }
    }

    @Override
    public BaseResponse<Void> validateLink(WalletRequest request) {
        PaytmWalletValidateLinkRequest paytmWalletValidateLinkRequest = (PaytmWalletValidateLinkRequest) request;
        try {
            URI uri = new URIBuilder(ACCOUNTS_URL + "/signin/validate/otp").build();
            String authHeader = String.format("Basic %s", Utils.encodeBase64(CLIENT_ID + ":" + SECRET));
            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", authHeader);
            RequestEntity<PaytmWalletValidateLinkRequest> requestEntity = new RequestEntity<>(paytmWalletValidateLinkRequest, headers, HttpMethod.POST, uri);
            PaytmWalletValidateLinkResponse paytmWalletValidateLinkResponse = null;

            logger.info("Validate paytm otp request: {}", requestEntity);

            ResponseEntity<PaytmWalletValidateLinkResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmWalletValidateLinkResponse.class);
            paytmWalletValidateLinkResponse = responseEntity.getBody();
            if (paytmWalletValidateLinkResponse != null && paytmWalletValidateLinkResponse.getStatus().equals(Status.SUCCESS)) {
                saveToken(paytmWalletValidateLinkResponse);
                return BaseResponse.<Void>builder().status(HttpStatus.OK).build();
            }
        } catch (HttpStatusCodeException e) {
            AnalyticService.update("otpValidated", false);
            logger.error(PAYTM_ERROR, "Error in response: {}", e.getResponseBodyAsString(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e, e.getResponseBodyAsString());
        } catch (Exception ex) {
            logger.error(PAYTM_ERROR, "Error in response: {}", ex.getMessage(), ex);
        }
        throw new WynkRuntimeException(PaymentErrorType.PAY998, PAYTM_ERROR);
    }

    private void saveToken(PaytmWalletValidateLinkResponse tokenResponse) {
        if (StringUtils.isBlank(tokenResponse.getAccess_token())) {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String walletUserId = sessionDTO.get(WALLET_USER_ID);
            String uid = sessionDTO.get(UID);
            Wallet wallet = new Wallet.Builder().paymentCode(PAYTM_WALLET).walletUserId(walletUserId)
                    .accessToken(tokenResponse.getAccess_token()).tokenValidity(tokenResponse.getExpires()).build();
            userPaymentsManager.saveWalletToken(uid, wallet);
        }
    }

    @Override
    public BaseResponse<Object> unlink(WalletRequest request) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String uid = sessionDTO.get(UID);
        userPaymentsManager.deletePaymentDetails(uid, PAYTM_WALLET);
        return BaseResponse.status(true);
    }

    @Override
    public BaseResponse<WalletBalanceResponse> balance() {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        Double amountInRs = sessionDTO.get(AMOUNT);
        String uid = sessionDTO.get(UID);
        String accessToken;
        UserPreferredPayment userPreferredPayment = userPaymentsManager.getPaymentDetails(uid, PAYTM_WALLET);
        if (Objects.nonNull(userPreferredPayment)) {
            Wallet wallet = (Wallet) userPreferredPayment.getOption();
            accessToken = wallet.getAccessToken();
            if (!validateAccessToken(uid, wallet)) {
                WalletBalanceResponse response = WalletBalanceResponse.builder().isLinked(false).build();
                return BaseResponse.<WalletBalanceResponse>builder().body(response).status(HttpStatus.OK).build();
            }
        } else {
            return BaseResponse.<WalletBalanceResponse>builder().body(WalletBalanceResponse.defaultUnlinkResponse())
                    .status(HttpStatus.OK).build();
        }

        try {
            URI uri = new URIBuilder(SERVICES_URL + "/paymentservices/pay/consult").build();
            ConsultBalanceRequest.ConsultBalanceRequestBody body = ConsultBalanceRequest.ConsultBalanceRequestBody.builder().userToken(accessToken)
                    .totalAmount(amountInRs).mid(MID).build();
            String jsonPayload = gson.toJson(body);
            logger.debug("Generating signature for payload: {}", jsonPayload);
            String signature = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, jsonPayload);
            ConsultBalanceRequest.ConsultBalanceRequestHead head = ConsultBalanceRequest.ConsultBalanceRequestHead.builder().clientId(CLIENT_ID).version("v1")
                    .requestTimestamp(System.currentTimeMillis() + "").signature(signature).channelId("WEB").build();
            ConsultBalanceRequest consultBalanceRequest = ConsultBalanceRequest.builder().head(head).body(body).build();
            RequestEntity<ConsultBalanceRequest> requestEntity = new RequestEntity<>(consultBalanceRequest, HttpMethod.POST, uri);
            logger.info("Paytm wallet balance request: {}", requestEntity);
            ResponseEntity<ConsultBalanceResponse> responseEntity = restTemplate.exchange(requestEntity, ConsultBalanceResponse.class);
            logger.info("Paytm wallet balance response: {}", responseEntity);
            AnalyticService.update("PAYTM_RESPONSE_CODE", responseEntity.getStatusCodeValue());
            ConsultBalanceResponse payTmResponse = responseEntity.getBody();
            //add a null check on body.
            if (payTmResponse != null && payTmResponse.getBody() != null && payTmResponse.getBody().getResultInfo().getResultStatus() == Status.SUCCESS) {
                WalletBalanceResponse response = WalletBalanceResponse.builder().isLinked(true)
                        .deficitBalance(payTmResponse.getBody().getDeficitAmount()).fundsSufficient(payTmResponse.getBody().isFundsSufficient()).build();
                return BaseResponse.<WalletBalanceResponse>builder().body(response).status(HttpStatus.OK).build();
            }

        } catch (HttpStatusCodeException e) {
            throw new RuntimeException("Http Status Exception Occurred");
        } catch (Exception e) {
            throw new RuntimeException("Unknown Exception Occurred");
        }
        return BaseResponse.<WalletBalanceResponse>builder().body(WalletBalanceResponse.defaultUnlinkResponse()).status(HttpStatus.OK).build();
    }

    private boolean validateAccessToken(String uid, Wallet wallet) {
        String accessToken = wallet.getAccessToken();
        String msisdn = wallet.getWalletUserId();
        logger.info("Validating access token for msisdn: {}, uid: {} with PayTM", msisdn, uid);
        if (StringUtils.isBlank(accessToken) || wallet.getTokenValidity() < System.currentTimeMillis()) {
            logger.info("Access token is expired or not present for: {}", msisdn);
            return false;
        }
        try {
            URI uri = new URIBuilder(ACCOUNTS_URL + "/user/details").build();
            HttpHeaders headers = new HttpHeaders();
            headers.add("session_token", accessToken);
            RequestEntity<ValidateTokenResponse> requestEntity = new RequestEntity<>(headers, HttpMethod.GET, uri);
            logger.info("Validate paytm access token request: {}", requestEntity);
            ResponseEntity<ValidateTokenResponse> responseEntity = restTemplate.exchange(requestEntity, ValidateTokenResponse.class);
            ValidateTokenResponse validateTokenResponse = responseEntity.getBody();
            if (validateTokenResponse != null) {
                //option to capture user's email.
                return validateTokenResponse.getExpires() > System.currentTimeMillis();
            }
        } catch (HttpStatusCodeException e) {
            logger.error(PAYTM_ERROR, "Error from paytm: {} , response: {}", e.getMessage(), e.getResponseBodyAsString(), e);
            throw new RuntimeException("Http Status Exception Occurred");
        } catch (Exception e) {
            throw new RuntimeException("General Exception occurred");
        }
        return false;
    }

    @Override
    public BaseResponse<Map<String, String>> addMoney(WalletRequest request) {
        PaytmWalletAddMoneyRequest paytmWalletAddMoneyRequest = (PaytmWalletAddMoneyRequest) request;
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String uid = sessionDTO.get(UID);
        String accessToken = "3f1fdc96-49e7-4046-b234-321d1fc92300"; //fetch from session
        if (StringUtils.isBlank(accessToken)) {
            logger.info("Access token is expired or not present for: {}", accessToken);
            throw new WynkRuntimeException("Access token is not present");
        }
        String sessionId = SessionContextHolder.get().getId().toString();
        sessionDTO.put(PLAN_ID, paytmWalletAddMoneyRequest.getPlanId());
        return addMoney(sessionId, uid, accessToken, String.valueOf(paytmWalletAddMoneyRequest.getAmountToCredit()));
    }

    private BaseResponse<Map<String, String>> addMoney(String sid, String uid, String accessToken, String amount) {
        try {
            String wynkCallbackURL = callBackUrl + sid;
            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put(PAYTM_REQUEST_TYPE, ADD_MONEY);
            parameters.put(PAYTM_MID, MID);
            parameters.put(PAYTM_REQUST_ORDER_ID, sid);
            parameters.put(PAYTM_CHANNEL_ID, PAYTM_WEB);
            parameters.put(PAYTM_INDUSTRY_TYPE_ID, RETAIL);
            parameters.put(PAYTM_REQUEST_CUST_ID, uid);
            parameters.put(PAYTM_REQUEST_TXN_AMOUNT, amount);
            parameters.put(PAYTM_REQUESTING_WEBSITE, paytmRequestingWebsite);
            parameters.put(PAYTM_SSO_TOKEN, accessToken);
            parameters.put(PAYTM_REQUEST_CALLBACK, wynkCallbackURL);
            parameters.put(PAYTM_CHECKSUMHASH, checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, parameters));
            String payTmRequestParams = gson.toJson(parameters);
            payTmRequestParams = EncryptionUtils.encrypt(payTmRequestParams, paymentEncryptionKey);
            Map<String, String> params = new HashMap<>();
            params.put(INFO, payTmRequestParams);
            return BaseResponse.<Map<String, String>>builder().body(params).status(HttpStatus.OK).build();
        } catch (HttpStatusCodeException e) {
            throw new WynkRuntimeException("Http Status Exception Occurred");
        } catch (Exception e) {
            throw new WynkRuntimeException("Exception occurred");
        }
    }

    @Override
    public BaseResponse<PaytmWalletLinkResponse> linkRequest(WalletRequest walletRequest) {
        PaytmWalletLinkRequest paytmWalletLinkRequest = (PaytmWalletLinkRequest) walletRequest;
        String phone = paytmWalletLinkRequest.getEncSi();
        logger.info("Sending OTP to {} via PayTM", phone);
        try {
            URI uri = new URIBuilder(ACCOUNTS_URL + "/signin/otp").build();
            PaytmWalletOtpRequest request = PaytmWalletOtpRequest.builder().phone(phone).clientId(CLIENT_ID)
                    .scope("wallet").responseType("token").build();
            RequestEntity<PaytmWalletOtpRequest> requestEntity = new RequestEntity<>(request, HttpMethod.POST, uri);
            logger.info("Paytm OTP request: {}", requestEntity);
            ResponseEntity<PaytmWalletLinkResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmWalletLinkResponse.class);
            logger.info("Paytm OTP response: {}", responseEntity);
            HttpStatus statusCode = responseEntity.getStatusCode();
            PaytmWalletLinkResponse paytmWalletLinkResponse = responseEntity.getBody();

            //TODO: refactor
            if (!statusCode.is2xxSuccessful() || paytmWalletLinkResponse == null) {
                PayTmErrorCodes errorCode = PayTmErrorCodes.UNKNOWN;
                if (paytmWalletLinkResponse != null) {
                    String responseCode = paytmWalletLinkResponse.getResponseCode();
                    errorCode = PayTmErrorCodes.resolveErrorCode(responseCode);
                }
                logger.error(HTTP_ERROR, "Error in sending otp. Reason: [{}]",
                        errorCode.getMessage());
            }

            if (paytmWalletLinkResponse.getStatus() == Status.FAILURE) {
                PayTmErrorCodes errorCode;
                String responseCode = paytmWalletLinkResponse.getResponseCode();
                errorCode = PayTmErrorCodes.resolveErrorCode(responseCode);
                logger.error(HTTP_ERROR, "Error in sending otp. Reason: [{}]",
                        errorCode.getMessage());
                return BaseResponse.<PaytmWalletLinkResponse>builder().body(paytmWalletLinkResponse).status(HttpStatus.OK).headers(requestEntity.getHeaders()).build();
            }

            String state = paytmWalletLinkResponse.getState(); // add state in session
            if (StringUtils.isBlank(state)) {
                throw new RuntimeException("Paytm responded with empty state for OTP response");
            }

            logger.info("Otp sent successfully. Status: {}", paytmWalletLinkResponse.getStatus());
            return BaseResponse.<PaytmWalletLinkResponse>builder().status(HttpStatus.OK).body(paytmWalletLinkResponse).build();
        } catch (HttpStatusCodeException e) {
            logger.error(PAYTM_ERROR, "Error from paytm: {}", e.getResponseBodyAsString(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, "Paytm error - " + e.getResponseBodyAsString());
        } catch (Exception e) {
            logger.error(PAYTM_ERROR, "Error from paytm: {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, "Paytm error - " + e.getMessage());
        }
    }

    @Override
    public UserPreferredPayment getUserPreferredPayments(String uid) {
        return userPaymentsManager.getPaymentDetails(uid, PAYTM_WALLET);
    }

}
