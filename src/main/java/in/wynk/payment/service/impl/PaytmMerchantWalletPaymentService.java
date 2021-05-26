package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.paytm.pg.merchant.CheckSumServiceHelper;
import in.wynk.common.dto.*;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.Status;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.common.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.ErrorCode;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.paytm.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractPaymentDetails;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.UserWalletDetails;
import in.wynk.payment.dto.response.paytm.*;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.util.*;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.logging.BaseLoggingMarkers.APPLICATION_ERROR;
import static in.wynk.payment.core.constant.PaymentCode.PAYTM_WALLET;
import static in.wynk.payment.core.constant.PaymentConstants.WALLET;
import static in.wynk.payment.core.constant.PaymentConstants.WALLET_USER_ID;
import static in.wynk.payment.core.constant.PaymentErrorType.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYTM_ERROR;
import static in.wynk.payment.dto.paytm.PayTmConstants.*;
import static in.wynk.payment.dto.paytm.PayTmConstants.PAYTM_CHECKSUMHASH;

@Slf4j
@Service(BeanConstant.PAYTM_MERCHANT_WALLET_SERVICE)
public class PaytmMerchantWalletPaymentService extends AbstractMerchantPaymentStatusService implements IRenewalMerchantWalletService, IUserPreferredPaymentService, IMerchantPaymentRefundService {

    @Value("${paytm.native.merchantId}")
    private String MID;

    @Value("${paytm.native.secret}")
    private String SECRET;

    @Value("${paytm.refund.api}")
    private String REFUND;

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

    @Value("${paytm.refundStatus.api}")
    private String REFUND_STATUS;

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
        super(paymentCachingService);
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.userPaymentsManager = userPaymentsManager;
        this.paymentCachingService = paymentCachingService;
        this.applicationEventPublisher = applicationEventPublisher;
        this.checkSumServiceHelper = CheckSumServiceHelper.getCheckSumServiceHelper();
    }

    @Override
    public BaseResponse<?> handleCallback(CallbackRequest callbackRequest) {
        Map<String, String> params = (Map<String, String>) callbackRequest.getBody();
        String status = params.get(PAYTM_STATUS);
        if (StringUtils.isBlank(status) || !status.equalsIgnoreCase(PAYTM_STATUS_SUCCESS)) {
            log.error(APPLICATION_ERROR, "Add money txn at paytm failed");
            throw new RuntimeException("Failed to add money to wallet");
        }
        log.info("Successfully added money to wallet. Now withdrawing amount");
        return doCharging(null);
    }

    @Override
    public BaseResponse<?> doCharging(ChargingRequest chargingRequest) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        Transaction transaction = TransactionContext.get();
        String redirectUrl = null;
        final String uid = transaction.getUid();
        final String msisdn = transaction.getMsisdn();
        final String sid = SessionContextHolder.getId();
        final int planId = transaction.getPlanId();
        final String deviceId = sessionDTO.get(DEVICE_ID);
        if (StringUtils.isBlank(msisdn) || StringUtils.isBlank(uid)) {
            throw new WynkRuntimeException("Linked Msisdn or UID not found for user");
        }
        Wallet wallet = getWallet(uid, deviceId);
        UserWalletDetails userWalletDetails = (UserWalletDetails) this.getUserPreferredPayments(UserPreferredPaymentsRequest.builder().planId(planId).uid(uid).deviceId(deviceId).build()).getData();
//        PaytmUserWalletDetails paytmWalletDetails = this.getUserPreferredPayments(UserPreferredPaymentsRequest.builder().planId(planId).uid(uid).build());
//        if (!paytmWalletDetails.isFundSufficient()) {
//            throw new WynkRuntimeException("Balance insufficient in linked wallet for this transaction to succeed");
//        }
        try {
            PaytmChargingResponse paytmChargingResponse = withdrawFromPaytm(transaction, wallet.getAccessToken());
            if (paytmChargingResponse != null && paytmChargingResponse.getStatus().equalsIgnoreCase(PAYTM_STATUS_SUCCESS)) {
                transaction.setStatus(TransactionStatus.SUCCESS.name());
                redirectUrl = successPage+sid;
            } else {
                transaction.setStatus(TransactionStatus.FAILURE.name());
                applicationEventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(paytmChargingResponse.getResponseCode()).description(paytmChargingResponse.getResponseMessage()).build());
            }
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.name());
            log.error(PAYTM_ERROR, "unable to charge due to ", e);
        } finally {
            if (StringUtils.isBlank(redirectUrl)) {
                redirectUrl = failurePage+sid;
            }
            return BaseResponse.<WynkResponse.WynkResponseWrapper>builder().status(HttpStatus.OK).body(WynkResponse.WynkResponseWrapper.builder().data(redirectUrl).build()).build();
        }
    }

    @Override
    public BaseResponse<?> refund(AbstractPaymentRefundRequest request) {
        Transaction refundTransaction = TransactionContext.get();
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        MerchantTransactionEvent.Builder merchantTransactionBuilder = MerchantTransactionEvent.builder(refundTransaction.getIdStr());
        PaytmPaymentRefundResponse.PaytmPaymentRefundResponseBuilder<?, ?> refundResponseBuilder = PaytmPaymentRefundResponse.builder()
                .uid(refundTransaction.getUid())
                .planId(refundTransaction.getPlanId())
                .itemId(refundTransaction.getItemId())
                .amount(refundTransaction.getAmount())
                .msisdn(refundTransaction.getMsisdn())
                .paymentEvent(refundTransaction.getType())
                .transactionId(refundTransaction.getIdStr())
                .clientAlias(refundTransaction.getClientAlias());
        try {
            PaytmPaymentRefundRequest refundRequest = (PaytmPaymentRefundRequest) request;
            URI uri = new URIBuilder(REFUND).build();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            PaytmRefundRequestBody body = PaytmRefundRequestBody.builder()
                    .mid(MID)
                    .txnType("REFUND")
                    .refId(refundTransaction.getIdStr())
                    .comments(refundRequest.getReason())
                    .txnId(refundRequest.getPaytmTxnId())
                    .orderId(refundRequest.getOriginalTransactionId())
                    .refundAmount(String.valueOf(refundTransaction.getAmount()))
                    .build();
            String jsonPayload = objectMapper.writeValueAsString(body);
            String signature = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, jsonPayload);
            log.info("Generated checksum: {} for payload: {}", signature, jsonPayload);
            PaytmRequestHead paytmRequestHead = PaytmRequestHead.builder().clientId(CLIENT_ID).version("v1").requestTimestamp(System.currentTimeMillis() + "").signature(signature).channelId("WEB").build();
            RequestEntity<PaytmRequest> requestEntity = new RequestEntity<>(PaytmRequest.builder().body(body).head(paytmRequestHead).build(), headers, HttpMethod.POST, uri);
            log.info("Paytm wallet charging status request: {}", requestEntity);
            merchantTransactionBuilder.request(requestEntity.getBody());
            ResponseEntity<PaytmResponse<PaytmRefundResponseBody>> responseEntity = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<PaytmResponse<PaytmRefundResponseBody>>() {
            });
            log.info("Paytm wallet charging status response: {}", responseEntity);
            merchantTransactionBuilder.response(responseEntity.getBody());
            PaytmResponse<PaytmRefundResponseBody> paytmRefundResponse = responseEntity.getBody();
            if (!paytmRefundResponse.getBody().getResultInfo().getResultStatus().equalsIgnoreCase(PAYTM_STATUS_PENDING)) {
                finalTransactionStatus = TransactionStatus.FAILURE;
                applicationEventPublisher.publishEvent(PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(String.valueOf(paytmRefundResponse.getBody().getResultInfo().getResultCode())).description(paytmRefundResponse.getBody().getResultInfo().getResultMsg()).build());
            } else {
                refundResponseBuilder.paytmTxnId(paytmRefundResponse.getBody().getOrderId());
                merchantTransactionBuilder.externalTransactionId(paytmRefundResponse.getBody().getOrderId());
                AnalyticService.update(EXTERNAL_TRANSACTION_ID, paytmRefundResponse.getBody().getOrderId());
            }
        } catch (WynkRuntimeException ex) {
            applicationEventPublisher.publishEvent(PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(ex.getErrorCode()).description(ex.getErrorTitle()).build());
            throw new WynkRuntimeException(PaymentErrorType.PAY020, ex, ex.getMessage());
        } catch (Exception ex) {
            throw new WynkRuntimeException(PaymentErrorType.PAY020, ex, ex.getMessage());
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
            applicationEventPublisher.publishEvent(merchantTransactionBuilder.build());
            return BaseResponse.builder().body(refundResponseBuilder.build()).status(HttpStatus.OK).build();
        }
    }

    @Override
    public void doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {}

    private PaytmChargingResponse withdrawFromPaytm(Transaction transaction, String accessToken) {
        MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            URI uri = new URIBuilder(AUTO_DEBIT).build();
            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put("MID", MID);
            parameters.put("ReqType", "WITHDRAW");
            parameters.put("TxnAmount", String.valueOf(transaction.getAmount()));
            parameters.put("AppIP", "45.251.51.117");
            parameters.put("OrderId", transaction.getIdStr());
            parameters.put("DeviceId", transaction.getMsisdn());
            parameters.put("Currency", "INR");
            parameters.put("SSOToken", accessToken);
            parameters.put("PaymentMode", "PPI");
            parameters.put("CustId", transaction.getUid());
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
            merchantTransactionEventBuilder.externalTransactionId(responseEntity.getBody().getTxnId()).response(responseEntity.getBody());
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

    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        Transaction transaction = TransactionContext.get();
        if (transactionStatusRequest instanceof ChargingTransactionReconciliationStatusRequest) {
            syncChargingTransactionFromSource(transaction);
        } else if (transactionStatusRequest instanceof RefundTransactionReconciliationStatusRequest) {
            syncRefundTransactionFromSource(transaction, transactionStatusRequest.getExtTxnId());
        } else {
            throw new WynkRuntimeException(PAY889, "Unknown transaction status request to process for uid: " + transaction.getUid());
        }
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.error(PaymentLoggingMarker.PAYTM_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at paytm end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY104);
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(PaymentLoggingMarker.PAYTM_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at paytm end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY103);
        }
        ChargingStatusResponse.ChargingStatusResponseBuilder responseBuilder = ChargingStatusResponse.builder()
                .tid(transaction.getIdStr())
                .planId(transaction.getPlanId())
                .transactionStatus(transaction.getStatus());
        if (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != PaymentEvent.POINT_PURCHASE) {
            responseBuilder.validity(paymentCachingService.validTillDate(transaction.getPlanId()));
        }
        return BaseResponse.<ChargingStatusResponse>builder().status(HttpStatus.OK).body(responseBuilder.build()).build();
    }

    private void syncRefundTransactionFromSource(Transaction transaction, String orderId) {
        MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            URI uri = new URIBuilder(REFUND_STATUS).build();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            PaytmStatusRequestBody body = PaytmStatusRequestBody.builder().mid(MID).orderId(orderId).refId(transaction.getIdStr()).build();
            String jsonPayload = objectMapper.writeValueAsString(body);
            String signature = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, jsonPayload);
            log.info("Generated checksum: {} for payload: {}", signature, jsonPayload);
            PaytmRequestHead paytmRequestHead = PaytmRequestHead.builder().clientId(CLIENT_ID).version("v1").requestTimestamp(System.currentTimeMillis() + "").signature(signature).channelId("WEB").build();
            RequestEntity<PaytmRequest> requestEntity = new RequestEntity<>(PaytmRequest.builder().body(body).head(paytmRequestHead).build(), headers, HttpMethod.POST, uri);
            log.info("Paytm wallet charging status request: {}", requestEntity);
            merchantTransactionEventBuilder.request(requestEntity.getBody());
            ResponseEntity<PaytmResponse<PaytmRefundStatusResponseBody>> responseEntity = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<PaytmResponse<PaytmRefundStatusResponseBody>>() {
            });
            log.info("Paytm wallet charging status response: {}", responseEntity);
            merchantTransactionEventBuilder.response(responseEntity.getBody());
            PaytmResponse<PaytmRefundStatusResponseBody> paytmRefundStatusResponse = responseEntity.getBody();
            merchantTransactionEventBuilder.externalTransactionId(paytmRefundStatusResponse.getBody().getOrderId());
            AnalyticService.update(EXTERNAL_TRANSACTION_ID, paytmRefundStatusResponse.getBody().getTxnId());
            syncTransactionWithSourceResponse(paytmRefundStatusResponse.getBody().getResultInfo());
            if (transaction.getStatus() == TransactionStatus.FAILURE) {
                if (!StringUtils.isEmpty(paytmRefundStatusResponse.getBody().getResultInfo().getResultCode()) || !StringUtils.isEmpty(paytmRefundStatusResponse.getBody().getResultInfo().getResultMsg())) {
                    applicationEventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(paytmRefundStatusResponse.getBody().getResultInfo().getResultCode()).description(paytmRefundStatusResponse.getBody().getResultInfo().getResultMsg()).build());
                }
            }
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.PAYTM_CHARGING_STATUS_VERIFICATION, "unable to execute syncChargingTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } finally {
            if (transaction.getType() != PaymentEvent.RENEW || transaction.getStatus() != TransactionStatus.FAILURE)
                applicationEventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    private void syncChargingTransactionFromSource(Transaction transaction) {
        MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            URI uri = new URIBuilder(TRANSACTION_STATUS).build();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            PaytmStatusRequestBody body = PaytmStatusRequestBody.builder().mid(MID).orderId(transaction.getIdStr()).txnType("WITHDRAW").build();
            String jsonPayload = objectMapper.writeValueAsString(body);
            String signature = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, jsonPayload);
            log.info("Generated checksum: {} for payload: {}", signature, jsonPayload);
            PaytmRequestHead paytmRequestHead = PaytmRequestHead.builder().clientId(CLIENT_ID).version("v1").requestTimestamp(System.currentTimeMillis() + "").signature(signature).channelId("WEB").build();
            RequestEntity<PaytmRequest> requestEntity = new RequestEntity<>(PaytmRequest.builder().body(body).head(paytmRequestHead).build(), headers, HttpMethod.POST, uri);
            log.info("Paytm wallet charging status request: {}", requestEntity);
            merchantTransactionEventBuilder.request(requestEntity.getBody());
            ResponseEntity<PaytmResponse<PaytmChargingStatusResponseBody>> responseEntity = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<PaytmResponse<PaytmChargingStatusResponseBody>>() {
            });
            log.info("Paytm wallet charging status response: {}", responseEntity);
            merchantTransactionEventBuilder.response(responseEntity.getBody());
            PaytmResponse<PaytmChargingStatusResponseBody> paytmChargingStatusResponse = responseEntity.getBody();
            merchantTransactionEventBuilder.externalTransactionId(paytmChargingStatusResponse.getBody().getTxnId());
            AnalyticService.update(EXTERNAL_TRANSACTION_ID, paytmChargingStatusResponse.getBody().getTxnId());
            syncTransactionWithSourceResponse(paytmChargingStatusResponse.getBody().getResultInfo());
            if (transaction.getStatus() == TransactionStatus.FAILURE) {
                if (!StringUtils.isEmpty(paytmChargingStatusResponse.getBody().getResultInfo().getResultCode()) || !StringUtils.isEmpty(paytmChargingStatusResponse.getBody().getResultInfo().getResultMsg())) {
                    applicationEventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(paytmChargingStatusResponse.getBody().getResultInfo().getResultCode()).description(paytmChargingStatusResponse.getBody().getResultInfo().getResultMsg()).build());
                }
            }
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.PAYTM_CHARGING_STATUS_VERIFICATION, "unable to execute syncChargingTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } finally {
            if (transaction.getType() != PaymentEvent.RENEW || transaction.getStatus() != TransactionStatus.FAILURE)
                applicationEventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    private void syncTransactionWithSourceResponse(PaytmResultInfo paytmResultInfo) {
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        if (Objects.nonNull(paytmResultInfo)) {
            if (paytmResultInfo.getResultStatus().equalsIgnoreCase(PAYTM_STATUS_SUCCESS)) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (paytmResultInfo.getResultStatus().equalsIgnoreCase(PAYTM_STATUS_PENDING)) {
                finalTransactionStatus = TransactionStatus.INPROGRESS;
            }
        }
        TransactionContext.get().setStatus(finalTransactionStatus.getValue());
    }

    @Override
    public BaseResponse<?> linkRequest(WalletLinkRequest walletLinkRequest) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<Void>builder();
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
            ResponseEntity<PaytmWalletLinkResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmWalletLinkResponse.class);
            log.info("Paytm OTP response: {}", responseEntity);
            PaytmWalletLinkResponse paytmWalletLinkResponse = responseEntity.getBody();
            if (paytmWalletLinkResponse.getStatus() == Status.SUCCESS && StringUtils.isNotBlank(paytmWalletLinkResponse.getState_token())) {
                log.info("Otp sent successfully. Status: {}", paytmWalletLinkResponse.getStatus());
                sessionDTO.put(STATE_TOKEN, paytmWalletLinkResponse.getState_token());
            } else {
                errorCode = ErrorCode.getErrorCodesFromExternalCode(paytmWalletLinkResponse.getResponseCode());
            }
        } catch (HttpStatusCodeException e) {
            log.error(PAYTM_ERROR, "Error from paytm: {}", e.getMessage(), e);
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(e.getResponseBodyAsString(), PaytmWalletLinkResponse.class).getResponseCode());
        } catch (Exception e) {
            errorCode = ErrorCode.UNKNOWN;
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
            return BaseResponse.<WynkResponseEntity.WynkBaseResponse>builder().status(httpStatus).body(builder.build()).build();
        }
    }

    @Override
    public BaseResponse<?> validateLink(WalletValidateLinkRequest walletValidateLinkRequest) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<Void>builder();
        try {
            URI uri = new URIBuilder(VALIDATE_OTP).build();
            HttpHeaders headers = getHttpHeaders();
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            walletValidateLinkRequest.setState_token(sessionDTO.get(STATE_TOKEN));
            RequestEntity<WalletValidateLinkRequest> requestEntity = new RequestEntity<>(walletValidateLinkRequest, headers, HttpMethod.POST, uri);
            log.info("Validate paytm otp request: {}", requestEntity);
            ResponseEntity<PaytmWalletValidateLinkResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmWalletValidateLinkResponse.class);
            PaytmWalletValidateLinkResponse paytmWalletValidateLinkResponse = responseEntity.getBody();
            if (paytmWalletValidateLinkResponse != null && paytmWalletValidateLinkResponse.getStatus().equals(Status.SUCCESS)) {
                saveToken(paytmWalletValidateLinkResponse);
            } else {
                errorCode = ErrorCode.getErrorCodesFromExternalCode(paytmWalletValidateLinkResponse.getResponseCode());
            }
        } catch (HttpStatusCodeException e) {
            AnalyticService.update("otpValidated", false);
            log.error(PAYTM_ERROR, "Error in response: {}", e.getMessage(), e);
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(e.getResponseBodyAsString(), PaytmWalletValidateLinkResponse.class).getResponseCode());
        } catch (Exception e) {
            errorCode = ErrorCode.UNKNOWN;
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
            return BaseResponse.<WynkResponseEntity.WynkBaseResponse>builder().status(httpStatus).body(builder.build()).build();
        }
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

    private void saveToken(PaytmWalletValidateLinkResponse tokenResponse) {
        if (!tokenResponse.getTokens().isEmpty()) {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String walletUserId = sessionDTO.get(WALLET_USER_ID);
            String uid = sessionDTO.get(UID);
            userPaymentsManager.savePaymentDetails(Wallet.builder()
                    .id(getKey(uid))
                    .walletUserId(walletUserId)
                    .tokenValidity(tokenResponse.getExpiry())
                    .accessToken(tokenResponse.getAccessToken())
                    .refreshToken(tokenResponse.getRefreshToken())
                    .build());
        }
    }

    @Override
    public BaseResponse<?> unlink() {
        try {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String uid = sessionDTO.get(UID);
            URI uri = new URIBuilder(REVOKE_ACCESS_TOKEN).build();
            HttpHeaders headers = getHttpHeaders();
            Wallet wallet = (Wallet) userPaymentsManager.getPaymentDetails(getKey(uid));
            headers.add(PAYTM_SESSION_TOKEN, wallet.getAccessToken());
            userPaymentsManager.deletePaymentDetails(wallet);
            RequestEntity requestEntity = new RequestEntity(headers, HttpMethod.DELETE, uri);
            restTemplate.exchange(requestEntity, String.class);
        } finally {
            return BaseResponse.<WynkResponseEntity.WynkBaseResponse>builder().status(HttpStatus.OK).body(WynkResponseEntity.WynkBaseResponse.builder().build()).build();
        }
    }

    @Override
    public BaseResponse<?> balance(int planId) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        return balance(planId, getWallet(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID)));
    }

    public BaseResponse<WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails>> balance(int planId, Wallet wallet) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        UserWalletDetails.UserWalletDetailsBuilder userWalletDetailsBuilder = UserWalletDetails.builder();
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<UserWalletDetails>builder();
        try {
            URI uri = new URIBuilder(FETCH_INSTRUMENT).build();
            PaytmBalanceRequestBody body = PaytmBalanceRequestBody.builder().userToken(wallet.getAccessToken()).mid(MID).txnAmount(paymentCachingService.getPlan(planId).getFinalPrice()).build();
            String jsonPayload = objectMapper.writeValueAsString(body);
            log.debug("Generating signature for payload: {}", jsonPayload);
            String signature = checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, jsonPayload);
            PaytmRequestHead head = PaytmRequestHead.builder().clientId(CLIENT_ID).version("v1").requestTimestamp(System.currentTimeMillis() + "").signature(signature).channelId("WEB").build();
            RequestEntity<PaytmRequest> requestEntity = new RequestEntity<>(PaytmRequest.builder().head(head).body(body).build(), HttpMethod.POST, uri);
            log.info("Paytm wallet balance request: {}", requestEntity);
            ResponseEntity<PaytmResponse<PaytmBalanceResponseBody>> responseEntity = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<PaytmResponse<PaytmBalanceResponseBody>>() {
            });
            log.info("Paytm wallet balance response: {}", responseEntity);
            AnalyticService.update("PAYTM_RESPONSE_CODE", responseEntity.getStatusCodeValue());
            PaytmResponse<PaytmBalanceResponseBody> payTmResponse = responseEntity.getBody();
            if (payTmResponse != null && payTmResponse.getBody() != null && payTmResponse.getBody().getResultInfo().getResultStatus().equals(Status.SUCCESS.toString())) {
                PaytmPayOption paytmPayOption = payTmResponse.getBody().getPayOptions().get(0);
                builder.data(userWalletDetailsBuilder
                        .linked(true)
                        .active(true)
                        .balance(paytmPayOption.getAmount())
                        .linkedMobileNo(wallet.getWalletUserId())
                        .deficitBalance(paytmPayOption.getDeficitAmount())
                        .expiredBalance(paytmPayOption.getExpiredAmount())
                        .build());
            } else {
                errorCode = ErrorCode.getErrorCodesFromExternalCode(payTmResponse.getBody().getResultInfo().getResultCode());
                builder.data(userWalletDetailsBuilder.linked(true).build());
            }
        } catch (HttpStatusCodeException e) {
            builder.data(userWalletDetailsBuilder.linked(true).build());
            log.error(PAYTM_ERROR, "Error in response: {}", e.getMessage(), e);
            errorCode = ErrorCode.getErrorCodesFromExternalCode(objectMapper.readValue(e.getResponseBodyAsString(), PaytmWalletValidateLinkResponse.class).getResponseCode());
        } catch (WynkRuntimeException e) {
            errorCode = handleWynkRunTimeException(e);
            httpStatus = e.getErrorType().getHttpResponseStatusCode();
        } catch (Exception e) {
            errorCode = ErrorCode.UNKNOWN;
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            handleError(errorCode, builder);
            return BaseResponse.<WynkResponseEntity.WynkBaseResponse<UserWalletDetails>>builder().status(httpStatus).body(builder.build()).build();
        }
    }

    private boolean validateAccessToken(Wallet wallet) {
        String accessToken = wallet.getAccessToken();
        String msisdn = wallet.getWalletUserId();
        log.info("Validating access token for linked mobile no: {}, uid: {} with PayTM", msisdn, wallet.getId().getUid());
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
            } catch (Exception e) {
                log.error(PAYTM_ERROR, "Error from paytm: {} , response: {}", e.getMessage(), e);
            }
        }
        return false;
    }

    private Wallet refreshAccessToken(Wallet wallet, String deviceId) {
        try {
            String refreshToken = wallet.getRefreshToken();
            String msisdn = wallet.getWalletUserId();
            log.info("Validating access token for linked Mobile No: {}, uid: {} with PayTM", msisdn, wallet.getId().getUid());
            URI uri = new URIBuilder(REFRESH_TOKEN).build();
            HttpHeaders headers = getHttpHeaders();
            PaytmRefreshTokenRequest paytmRefreshTokenRequest = PaytmRefreshTokenRequest.builder().deviceId(deviceId).grantType("refresh_token").refreshToken(refreshToken).build();
            RequestEntity<PaytmRefreshTokenRequest> requestEntity = new RequestEntity<>(paytmRefreshTokenRequest, headers, HttpMethod.POST, uri);
            log.info("Validate paytm access token request: {}", requestEntity);
            ResponseEntity<PaytmRefreshTokenResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmRefreshTokenResponse.class);
            PaytmRefreshTokenResponse paytmRefreshTokenResponse = responseEntity.getBody();
            if (responseEntity.getStatusCode().is2xxSuccessful() && Objects.nonNull(paytmRefreshTokenResponse)) {
                wallet = Wallet.builder()
                        .id(wallet.getId())
                        .walletUserId(wallet.getWalletUserId())
                        .refreshToken(wallet.getRefreshToken())
                        .accessToken(paytmRefreshTokenResponse.getAccessToken())
                        .tokenValidity(paytmRefreshTokenResponse.getExpiresIn())
                        .build();
                userPaymentsManager.savePaymentDetails(wallet);
            }
        } catch (Exception e) {
            log.error(PAYTM_ERROR, "Error from paytm: {} , response: {}", e.getMessage(), e);
        } finally {
            return wallet;
        }
    }

    @Override
    public BaseResponse<?> addMoney(WalletAddMoneyRequest walletAddMoneyRequest) {
        Transaction transaction = TransactionContext.get();
        String uid = transaction.getUid();
        return addMoney(transaction.getIdStr(), uid, getWallet(uid, walletAddMoneyRequest.getDeviceId()).getAccessToken(), walletAddMoneyRequest.getAmountToCredit());
    }

    public BaseResponse<?> addMoney(String txnId, String uid, String accessToken, double amount) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder = WynkResponseEntity.WynkBaseResponse.<Map<String, String>>builder();
        try {
            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put(PAYTM_REQUEST_TYPE, ADD_MONEY);
            parameters.put(PAYTM_MID, MID);
            parameters.put(PAYTM_REQUST_ORDER_ID, txnId);
            parameters.put(PAYTM_REQUEST_CUST_ID, uid);
            parameters.put(PAYTM_REQUEST_TXN_AMOUNT, String.valueOf(amount));
            parameters.put(PAYTM_CHANNEL_ID, PAYTM_WEB);
            parameters.put(PAYTM_INDUSTRY_TYPE_ID, RETAIL);
            parameters.put(PAYTM_REQUESTING_WEBSITE, paytmRequestingWebsite);
            parameters.put(PAYTM_SSO_TOKEN, accessToken);
            parameters.put(PAYTM_REQUEST_CALLBACK, callBackUrl+SessionContextHolder.getId());
            parameters.put(PAYTM_CHECKSUMHASH, checkSumServiceHelper.genrateCheckSum(MERCHANT_KEY, parameters));
            String payTmRequestParams = objectMapper.writeValueAsString(parameters);
            payTmRequestParams = EncryptionUtils.encrypt(payTmRequestParams, paymentEncryptionKey);
            Map<String, String> params = new HashMap<>();
            params.put(INFO, payTmRequestParams);
            builder.data(params);
        } catch (WynkRuntimeException e) {
            errorCode = handleWynkRunTimeException(e);
            httpStatus = e.getErrorType().getHttpResponseStatusCode();
        } catch (Exception e) {
            errorCode = ErrorCode.UNKNOWN;
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            handleError(errorCode, builder);
            return BaseResponse.<WynkResponseEntity.WynkBaseResponse>builder().status(httpStatus).body(builder.build()).build();
        }
    }

    @Override
    public WynkResponseEntity.WynkBaseResponse<AbstractPaymentDetails> getUserPreferredPayments(UserPreferredPaymentsRequest userPreferredPaymentsRequest) {
        return this.balance(userPreferredPaymentsRequest.getPlanId(), getWallet(userPreferredPaymentsRequest.getUid(), userPreferredPaymentsRequest.getDeviceId())).getBody();
    }

    private Wallet getWallet(String uid, String deviceId) {
        UserPreferredPayment userPreferredPayment = userPaymentsManager.getPaymentDetails(getKey(uid));
        if (Objects.nonNull(userPreferredPayment)) {
            Wallet wallet = (Wallet) userPreferredPayment;
            String accessToken = wallet.getAccessToken();
            if (StringUtils.isBlank(accessToken) || wallet.getTokenValidity() < System.currentTimeMillis()) {
                wallet = refreshAccessToken(wallet, deviceId);
            }
            return wallet;
        } else {
            throw new WynkRuntimeException(PAY202);
        }
    }

    private Key getKey(String uid) {
        return Key.builder().uid(uid).paymentGroup(WALLET).paymentCode(PAYTM_WALLET.name()).build();
    }

    private ErrorCode handleWynkRunTimeException(WynkRuntimeException e) {
        ErrorCode errorCode = ErrorCode.UNKNOWN;
        errorCode.setInternalCode(e.getErrorCode());
        errorCode.setInternalMessage(e.getErrorTitle());
        return errorCode;
    }

    private void handleError(ErrorCode errorCode, WynkResponseEntity.WynkBaseResponse.WynkBaseResponseBuilder builder) {
        if (Objects.nonNull(errorCode)) {
            if (errorCode == ErrorCode.UNKNOWN) {
                builder.error(TechnicalErrorDetails.builder().code(errorCode.getInternalCode()).description(errorCode.getInternalMessage()).build()).success(false);
            } else {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
        }
    }

}