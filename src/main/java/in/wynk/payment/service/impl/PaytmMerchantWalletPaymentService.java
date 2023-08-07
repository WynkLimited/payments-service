package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.paytm.pg.merchant.CheckSumServiceHelper;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.dto.StandardBusinessErrorDetails;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.Status;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.common.utils.Utils;
import in.wynk.error.codes.core.dao.entity.ErrorCode;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.PlanDetails;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.paytm.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.UserWalletDetails;
import in.wynk.payment.dto.response.WalletTopUpResponse;
import in.wynk.payment.dto.response.paytm.*;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.DiscountUtils;
import in.wynk.payment.utils.PropertyResolverUtils;
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
import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.exception.WynkErrorType.UT022;
import static in.wynk.logging.BaseLoggingMarkers.APPLICATION_ERROR;
import static in.wynk.payment.constant.WalletConstants.WALLET;
import static in.wynk.payment.constant.WalletConstants.WALLET_USER_ID;
import static in.wynk.payment.core.constant.BeanConstant.PAYTM_MERCHANT_WALLET_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.CALLBACK_PAYLOAD_PARSING_FAILURE;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYTM_ERROR;
import static in.wynk.payment.dto.paytm.PayTmConstants.*;

@Slf4j
@Service(PAYTM_MERCHANT_WALLET_SERVICE)
public class PaytmMerchantWalletPaymentService extends AbstractMerchantPaymentStatusService implements IMerchantPaymentCallbackService<PaytmCallbackResponse, PaytmCallbackRequestPayload>, IMerchantPaymentChargingService<PaytmAutoDebitChargingResponse, AbstractChargingRequest<?>>, IUserPreferredPaymentService<UserWalletDetails, PreferredPaymentDetailsRequest<?>>, IWalletLinkService<Void, WalletLinkRequest>, IWalletValidateLinkService<Void, WalletValidateLinkRequest>, IWalletDeLinkService<Void, WalletDeLinkRequest>, IWalletBalanceService<UserWalletDetails, WalletBalanceRequest>, IMerchantPaymentRefundService<PaytmPaymentRefundResponse, PaytmPaymentRefundRequest>, IWalletTopUpService<WalletTopUpResponse, WalletTopUpRequest<?>> {

    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final IUserPaymentsManager userPaymentsManager;
    private final CheckSumServiceHelper checkSumServiceHelper;
    private final IErrorCodesCacheService errorCodesCacheServiceImpl;
    private final ApplicationEventPublisher applicationEventPublisher;

    @Value("${paytm.refund.api}")
    private String REFUND;
    @Value("${paytm.sendOtp.api}")
    private String SEND_OTP;
    @Value("${paytm.autoDebit.api}")
    private String AUTO_DEBIT;
    @Value("${payment.success.page}")
    private String successPage;
    @Value("${payment.failure.page}")
    private String failurePage;
    @Value("${paytm.validateOtp.api}")
    private String VALIDATE_OTP;
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
    @Value("${payment.encKey}")
    private String paymentEncryptionKey;
    @Value("${paytm.requesting.website}")
    private String paytmRequestingWebsite;

    public PaytmMerchantWalletPaymentService(ObjectMapper objectMapper, @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, IUserPaymentsManager userPaymentsManager, PaymentCachingService paymentCachingService, ApplicationEventPublisher applicationEventPublisher, IErrorCodesCacheService errorCodesCacheServiceImpl) {
        super(paymentCachingService, errorCodesCacheServiceImpl);
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.userPaymentsManager = userPaymentsManager;
        this.applicationEventPublisher = applicationEventPublisher;
        this.errorCodesCacheServiceImpl = errorCodesCacheServiceImpl;
        this.checkSumServiceHelper = CheckSumServiceHelper.getCheckSumServiceHelper();
    }

    @Override
    public WynkResponseEntity<PaytmCallbackResponse> handleCallback(PaytmCallbackRequestPayload callbackRequest) {
        final String status = callbackRequest.getStatus();
        if (StringUtils.isBlank(status) || !status.equalsIgnoreCase(PAYTM_STATUS_SUCCESS)) {
            log.error(APPLICATION_ERROR, "Add money txn at paytm failed");
            throw new RuntimeException("Failed to add money to wallet");
        }
        log.info("Successfully added money to wallet. Now withdrawing amount");
        final WynkResponseEntity<PaytmAutoDebitChargingResponse> baseResponse = charge(null);
        final PaytmAutoDebitChargingResponse chargingResponse = baseResponse.getBody().getData();
        final PaytmCallbackResponse callbackResponse = PaytmCallbackResponse.builder().redirectUrl(chargingResponse.getRedirectUrl()).info(chargingResponse.getInfo()).deficit(chargingResponse.isDeficit()).build();
        return WynkResponseEntity.<PaytmCallbackResponse>builder().success(baseResponse.getBody().isSuccess()).status(baseResponse.getStatus()).headers(baseResponse.getHeaders()).error(baseResponse.getBody().getError()).data(callbackResponse).build();
    }

    @Override
    public PaytmCallbackRequestPayload parseCallback(Map<String, Object> payload) {
        try {
            final SessionDTO sessionDTO = SessionContextHolder.getBody();
            final String transactionId = sessionDTO.get(TRANSACTION_ID);
            return PaytmCallbackRequestPayload.builder().transactionId(transactionId).build();
        } catch (Exception e) {
            log.error(CALLBACK_PAYLOAD_PARSING_FAILURE, "Unable to parse callback payload due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY006, e);
        }
    }

    @Override
    public WynkResponseEntity<PaytmAutoDebitChargingResponse> charge(AbstractChargingRequest<?> chargingRequest) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        Transaction transaction = TransactionContext.get();
        String redirectUrl = null;
        final String uid = transaction.getUid();
        final String msisdn = transaction.getMsisdn();
        final String sid = SessionContextHolder.getId();
        final String deviceId = sessionDTO.get(DEVICE_ID);
        if (StringUtils.isBlank(msisdn) || StringUtils.isBlank(uid)) {
            throw new WynkRuntimeException("Linked Msisdn or UID not found for user");
        }
        Wallet wallet = getWallet(transaction.getClientAlias(), getKey(uid, deviceId));
        UserWalletDetails userWalletDetails = this.balance(transaction.getClientAlias(), transaction.getAmount(), wallet).getBody().getData();
        if (userWalletDetails.getDeficitBalance() > 0) {
            throw new WynkRuntimeException("Balance insufficient in linked wallet for this transaction to succeed");
        }
        try {
            PaytmChargingResponse paytmChargingResponse = withdrawFromPaytm(transaction, wallet.getAccessToken());
            if (paytmChargingResponse != null && paytmChargingResponse.getStatus().equalsIgnoreCase(PAYTM_STATUS_SUCCESS)) {
                transaction.setStatus(TransactionStatus.SUCCESS.name());
                redirectUrl = successPage + sid;
            } else {
                transaction.setStatus(TransactionStatus.FAILURE.name());
                applicationEventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(paytmChargingResponse.getResponseCode()).description(paytmChargingResponse.getResponseMessage()).build());
            }
        } catch (Exception e) {
            transaction.setStatus(TransactionStatus.FAILURE.name());
            log.error(PAYTM_ERROR, "unable to charge due to ", e);
        } finally {
            if (StringUtils.isBlank(redirectUrl)) {
                redirectUrl = failurePage + sid;
            }
            return WynkResponseEntity.<PaytmAutoDebitChargingResponse>builder().data(PaytmAutoDebitChargingResponse.builder().build()).build();
        }
    }

    @Override
    public WynkResponseEntity<PaytmPaymentRefundResponse> refund(PaytmPaymentRefundRequest refundRequest) {
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
        WynkResponseEntity.WynkResponseEntityBuilder<PaytmPaymentRefundResponse> responseBuilder = WynkResponseEntity.builder();
        try {
            URI uri = new URIBuilder(REFUND).build();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            final String merchantId = PropertyResolverUtils.resolve(refundTransaction.getClientAlias(), PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_ID);
            final String merchantSecret = PropertyResolverUtils.resolve(refundTransaction.getClientAlias(), PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_SECRET);
            PaytmRefundRequestBody body = PaytmRefundRequestBody.builder()
                    .mid(merchantId)
                    .txnType("REFUND")
                    .refId(refundTransaction.getIdStr())
                    .comments(refundRequest.getReason())
                    .txnId(refundRequest.getPaytmTxnId())
                    .orderId(refundRequest.getOriginalTransactionId())
                    .refundAmount(String.valueOf(refundTransaction.getAmount()))
                    .build();
            String jsonPayload = objectMapper.writeValueAsString(body);
            String signature = checkSumServiceHelper.genrateCheckSum(merchantSecret, jsonPayload);
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
                PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(String.valueOf(paytmRefundResponse.getBody().getResultInfo().getResultCode())).description(paytmRefundResponse.getBody().getResultInfo().getResultMsg()).build();
                responseBuilder.success(false).error(StandardBusinessErrorDetails.builder().code(errorEvent.getCode()).description(errorEvent.getDescription()).build());
                applicationEventPublisher.publishEvent(errorEvent);
            } else {
                refundResponseBuilder.paytmTxnId(paytmRefundResponse.getBody().getOrderId());
                merchantTransactionBuilder.externalTransactionId(paytmRefundResponse.getBody().getOrderId());
                AnalyticService.update(EXTERNAL_TRANSACTION_ID, paytmRefundResponse.getBody().getOrderId());
            }
        } catch (Exception ex) {
            PaymentErrorType errorType = PaymentErrorType.PAY020;
            PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(errorType.getErrorCode()).description(errorType.getErrorMessage()).build();
            responseBuilder.status(errorType.getHttpResponseStatusCode()).success(false).error(TechnicalErrorDetails.builder().code(errorEvent.getCode()).description(errorEvent.getDescription()).build());
            applicationEventPublisher.publishEvent(errorEvent);
            log.error(errorType.getMarker(), ex.getMessage(), ex);
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
            applicationEventPublisher.publishEvent(merchantTransactionBuilder.build());
            return responseBuilder.data(refundResponseBuilder.build()).build();
        }
    }

    private PaytmChargingResponse withdrawFromPaytm(Transaction transaction, String accessToken) {
        MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            URI uri = new URIBuilder(AUTO_DEBIT).build();
            TreeMap<String, String> parameters = new TreeMap<>();
            final String merchantId = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_ID);
            final String merchantSecret = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_SECRET);
            parameters.put("MID", merchantId);
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
            String checkSum = checkSumServiceHelper.genrateCheckSum(merchantSecret, parameters);
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
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        Transaction transaction = TransactionContext.get();
        if (transactionStatusRequest instanceof ChargingTransactionReconciliationStatusRequest) {
            syncChargingTransactionFromSource(transaction);
        } else if (transactionStatusRequest instanceof RefundTransactionReconciliationStatusRequest) {
            syncRefundTransactionFromSource(transaction, transactionStatusRequest.getExtTxnId());
        } else {
            throw new WynkRuntimeException("Invalid transaction request type: " + transactionStatusRequest.getClass());
        }
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.warn(PaymentLoggingMarker.PAYTM_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at paytm end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY104);
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.warn(PaymentLoggingMarker.PAYTM_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at paytm end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY103);
        }
        ChargingStatusResponse.ChargingStatusResponseBuilder responseBuilder = ChargingStatusResponse.builder()
                .tid(transaction.getIdStr())
                .planId(transaction.getPlanId())
                .transactionStatus(transaction.getStatus());
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(responseBuilder.build()).build();
    }

    private void syncRefundTransactionFromSource(Transaction transaction, String orderId) {
        MerchantTransactionEvent.Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            URI uri = new URIBuilder(REFUND_STATUS).build();
            HttpHeaders headers = new HttpHeaders();
            headers.add("Content-Type", "application/json");
            final String merchantId = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_ID);
            final String merchantSecret = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_SECRET);
            PaytmStatusRequestBody body = PaytmStatusRequestBody.builder().mid(merchantId).orderId(orderId).refId(transaction.getIdStr()).build();
            String jsonPayload = objectMapper.writeValueAsString(body);
            String signature = checkSumServiceHelper.genrateCheckSum(merchantSecret, jsonPayload);
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
            final String merchantId = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_ID);
            final String merchantSecret = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_SECRET);
            headers.add("Content-Type", "application/json");
            PaytmStatusRequestBody body = PaytmStatusRequestBody.builder().mid(merchantId).orderId(transaction.getIdStr()).txnType("WITHDRAW").build();
            String jsonPayload = objectMapper.writeValueAsString(body);
            String signature = checkSumServiceHelper.genrateCheckSum(merchantSecret, jsonPayload);
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
    public WynkResponseEntity<Void> link(WalletLinkRequest walletLinkRequest) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkResponseEntityBuilder<Void> builder = WynkResponseEntity.builder();
        try {
            String phone = walletLinkRequest.getWalletUserId();
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            AnalyticService.update(UID, sessionDTO.<String>get(UID));
            sessionDTO.put(WALLET_USER_ID, phone);
            log.info("Sending OTP to {} via PayTM", phone);
            URI uri = new URIBuilder(SEND_OTP).build();
            HttpHeaders headers = getHttpHeaders(sessionDTO.get(DEVICE_ID), walletLinkRequest.getClient());
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
                errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(paytmWalletLinkResponse.getResponseCode());
            }
        } catch (HttpStatusCodeException e) {
            log.error(PAYTM_ERROR, "Error from paytm: {}", e.getMessage(), e);
            errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(objectMapper.readValue(e.getResponseBodyAsString(), PaytmWalletLinkResponse.class).getResponseCode());
        } catch (Exception e) {
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
            return builder.status(httpStatus).build();
        }
    }

    @Override
    public WynkResponseEntity<Void> validate(WalletValidateLinkRequest walletValidateLinkRequest) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkResponseEntityBuilder<Void> builder = WynkResponseEntity.builder();
        try {
            URI uri = new URIBuilder(VALIDATE_OTP).build();
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            AnalyticService.update(UID, sessionDTO.<String>get(UID));
            HttpHeaders headers = getHttpHeaders(walletValidateLinkRequest.getClient(), sessionDTO.get(DEVICE_ID));
            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put("otp", walletValidateLinkRequest.getOtp());
            parameters.put("state_token", sessionDTO.get(STATE_TOKEN));
            RequestEntity<TreeMap<String, String>> requestEntity = new RequestEntity<>(parameters, headers, HttpMethod.POST, uri);
            log.info("Validate paytm otp request: {}", requestEntity);
            ResponseEntity<PaytmWalletValidateLinkResponse> responseEntity = restTemplate.exchange(requestEntity, PaytmWalletValidateLinkResponse.class);
            PaytmWalletValidateLinkResponse paytmWalletValidateLinkResponse = responseEntity.getBody();
            if (paytmWalletValidateLinkResponse != null && paytmWalletValidateLinkResponse.getStatus().equals(Status.SUCCESS)) {
                saveToken(paytmWalletValidateLinkResponse);
            } else {
                errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(paytmWalletValidateLinkResponse.getResponseCode());
            }
        } catch (HttpStatusCodeException e) {
            AnalyticService.update("otpValidated", false);
            log.error(PAYTM_ERROR, "Error in response: {}", e.getMessage(), e);
            errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(objectMapper.readValue(e.getResponseBodyAsString(), PaytmWalletValidateLinkResponse.class).getResponseCode());
        } catch (Exception e) {
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            if (Objects.nonNull(errorCode)) {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
            return builder.status(httpStatus).build();
        }
    }

    private HttpHeaders getHttpHeaders(String client, String deviceId) {
        final String merchantClientId = PropertyResolverUtils.resolve(client, PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_CLIENT_ID);
        final String token = PropertyResolverUtils.resolve(client, PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_TOKEN);
        String authHeader = String.format("Basic %s", Utils.encodeBase64(merchantClientId + ":" + token));
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", authHeader);
        headers.add("Content-Type", "application/json");
        if (StringUtils.isNotBlank(deviceId)) {
            headers.add("x-device-identifier", deviceId);
        }
        return headers;
    }

    private void saveToken(PaytmWalletValidateLinkResponse tokenResponse) {
        if (!tokenResponse.getTokens().isEmpty()) {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            String walletUserId = sessionDTO.get(WALLET_USER_ID);
            userPaymentsManager.save(Wallet.builder()
                    .walletUserId(walletUserId)
                    .tokenValidity(tokenResponse.getExpiry())
                    .accessToken(tokenResponse.getAccessToken())
                    .refreshToken(tokenResponse.getRefreshToken())
                    .id(getKey(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID)))
                    .build());
        }
    }

    @Override
    public WynkResponseEntity<Void> deLink(WalletDeLinkRequest request) {
        try {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            Wallet wallet = getWallet(request.getClient(), getKey(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID)));
            URI uri = new URIBuilder(REVOKE_ACCESS_TOKEN).build();
            HttpHeaders headers = getHttpHeaders(request.getClient(), wallet.getId().getDeviceId());
            headers.add(PAYTM_SESSION_TOKEN, wallet.getAccessToken());
            userPaymentsManager.delete(wallet);
            RequestEntity requestEntity = new RequestEntity(headers, HttpMethod.DELETE, uri);
            restTemplate.exchange(requestEntity, String.class);
        } finally {
            return WynkResponseEntity.<Void>builder().build();
        }
    }

    @Override
    public WynkResponseEntity<UserWalletDetails> balance(WalletBalanceRequest request) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        final double finalAmount = DiscountUtils.compute(null, PlanDetails.builder().planId(request.getPlanId()).build());
        return balance(request.getClient(), finalAmount, getWallet(request.getClient(), getKey(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID))));
    }

    public WynkResponseEntity<UserWalletDetails> balance(String client, double finalAmount, Wallet wallet) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        UserWalletDetails.UserWalletDetailsBuilder userWalletDetailsBuilder = UserWalletDetails.builder();
        WynkResponseEntity.WynkResponseEntityBuilder<UserWalletDetails> builder = WynkResponseEntity.builder();
        try {
            URI uri = new URIBuilder(FETCH_INSTRUMENT).build();
            userWalletDetailsBuilder.linked(true).linkedMobileNo(wallet.getWalletUserId());
            final String merchantId = PropertyResolverUtils.resolve(client, PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_ID);
            final String merchantSecret = PropertyResolverUtils.resolve(client, PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_SECRET);
            PaytmBalanceRequestBody body = PaytmBalanceRequestBody.builder().userToken(wallet.getAccessToken()).mid(merchantId).txnAmount(finalAmount).build();
            String jsonPayload = objectMapper.writeValueAsString(body);
            log.debug("Generating signature for payload: {}", jsonPayload);
            String signature = checkSumServiceHelper.genrateCheckSum(merchantSecret, jsonPayload);
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
                        .active(true)
                        .balance(paytmPayOption.getAmount())
                        .deficitBalance(paytmPayOption.getDeficitAmount())
                        .expiredBalance(paytmPayOption.getExpiredAmount())
                        .addMoneyAllowed(paytmPayOption.isAddMoneyAllowed())
                        .build());
            } else {
                errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(payTmResponse.getBody().getResultInfo().getResultCode());
            }
        } catch (HttpStatusCodeException e) {
            log.error(PAYTM_ERROR, "Error in response: {}", e.getMessage(), e);
            errorCode = errorCodesCacheServiceImpl.getErrorCodeByExternalCode(objectMapper.readValue(e.getResponseBodyAsString(), PaytmWalletValidateLinkResponse.class).getResponseCode());
        } catch (WynkRuntimeException e) {
            errorCode = handleWynkRunTimeException(e);
            httpStatus = e.getErrorType().getHttpResponseStatusCode();
        } catch (Exception e) {
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            builder.data(userWalletDetailsBuilder.build());
            handleError(errorCode, builder);
            return builder.status(httpStatus).build();
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

    private Wallet refreshAccessToken(String client, Wallet wallet) {
        try {
            String refreshToken = wallet.getRefreshToken();
            String msisdn = wallet.getWalletUserId();
            log.info("Validating access token for linked Mobile No: {}, uid: {} with PayTM", msisdn, wallet.getId().getUid());
            URI uri = new URIBuilder(REFRESH_TOKEN).build();
            HttpHeaders headers = getHttpHeaders(client, wallet.getId().getDeviceId());
            PaytmRefreshTokenRequest paytmRefreshTokenRequest = PaytmRefreshTokenRequest.builder().deviceId(wallet.getId().getDeviceId()).grantType("refresh_token").refreshToken(refreshToken).build();
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
                userPaymentsManager.save(wallet);
            }
        } catch (Exception e) {
            log.error(PAYTM_ERROR, "Error from paytm: {} , response: {}", e.getMessage(), e);
        } finally {
            return wallet;
        }
    }

    @Override
    public WynkResponseEntity<WalletTopUpResponse> topUp(WalletTopUpRequest<?> walletAddMoneyRequest) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        return addMoney(sessionDTO.get(CLIENT), ((IChargingDetails) walletAddMoneyRequest.getPurchaseDetails()).getCallbackDetails().getCallbackUrl(), 0, getWallet(sessionDTO.get(CLIENT), getKey(sessionDTO.get(UID), sessionDTO.get(DEVICE_ID))));
    }

    public WynkResponseEntity<WalletTopUpResponse> addMoney(String client, String callBackUrl, double amount, Wallet wallet) {
        ErrorCode errorCode = null;
        HttpStatus httpStatus = HttpStatus.OK;
        WynkResponseEntity.WynkResponseEntityBuilder<WalletTopUpResponse> builder = WynkResponseEntity.builder();
        try {
            final String merchantId = PropertyResolverUtils.resolve(client, PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_ID);
            final String merchantSecret = PropertyResolverUtils.resolve(client, PAYTM_MERCHANT_WALLET_SERVICE.toLowerCase(), MERCHANT_SECRET);
            TreeMap<String, String> parameters = new TreeMap<>();
            parameters.put(PAYTM_REQUEST_TYPE, ADD_MONEY);
            parameters.put(PAYTM_MID, merchantId);
            parameters.put(PAYTM_REQUST_ORDER_ID, TransactionContext.get().getIdStr());
            parameters.put(PAYTM_REQUEST_CUST_ID, wallet.getId().getUid());
            parameters.put(PAYTM_REQUEST_TXN_AMOUNT, String.valueOf(amount));
            parameters.put(PAYTM_CHANNEL_ID, PAYTM_WEB);
            parameters.put(PAYTM_INDUSTRY_TYPE_ID, RETAIL);
            parameters.put(PAYTM_REQUESTING_WEBSITE, paytmRequestingWebsite);
            parameters.put(PAYTM_SSO_TOKEN, wallet.getAccessToken());
            parameters.put(PAYTM_REQUEST_CALLBACK, callBackUrl);
            parameters.put(PAYTM_CHECKSUMHASH, checkSumServiceHelper.genrateCheckSum(merchantSecret, parameters));
            String payTmRequestParams = objectMapper.writeValueAsString(parameters);
            payTmRequestParams = EncryptionUtils.encrypt(payTmRequestParams, paymentEncryptionKey);
            builder.data(WalletTopUpResponse.builder().info(payTmRequestParams).build());
        } catch (WynkRuntimeException e) {
            errorCode = handleWynkRunTimeException(e);
            httpStatus = e.getErrorType().getHttpResponseStatusCode();
        } catch (Exception e) {
            errorCode = errorCodesCacheServiceImpl.getDefaultUnknownErrorCode();
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        } finally {
            handleError(errorCode, builder);
            return builder.status(httpStatus).build();
        }
    }

    @Override
    @ClientAware(clientAlias = "#request.clientAlias")
    public WynkResponseEntity<UserWalletDetails> getUserPreferredPayments(PreferredPaymentDetailsRequest<?> request) {
        try {
            final double finalAmount = DiscountUtils.compute(request.getCouponId(), request.getProductDetails());
            return this.balance(request.getClientAlias(), finalAmount, getWallet(request.getClientAlias(), request.getPreferredPayment()));
        } catch (WynkRuntimeException e) {
            return WynkResponseEntity.<UserWalletDetails>builder().error(TechnicalErrorDetails.builder().code(e.getErrorCode()).description(e.getMessage()).build()).data(UserWalletDetails.builder().build()).success(false).build();
        }
    }

    private Wallet getWallet(String client, UserPreferredPayment userPreferredPayment) {
        try {
            Wallet wallet = (Wallet) userPreferredPayment;
            if (StringUtils.isBlank(wallet.getAccessToken())) {
                throw new WynkRuntimeException(UT022);
            } else if (wallet.getTokenValidity() < System.currentTimeMillis()) {
                return refreshAccessToken(client, wallet);
            } else {
                return wallet;
            }
        } catch (Exception e) {
            throw new WynkRuntimeException(UT022);
        }
    }

    private Wallet getWallet(String client, SavedDetailsKey key) {
        Map<SavedDetailsKey, UserPreferredPayment> userPreferredPaymentMap = userPaymentsManager.get(key.getUid()).stream().collect(Collectors.toMap(UserPreferredPayment::getId, Function.identity()));
        return getWallet(client, userPreferredPaymentMap.getOrDefault(key, null));
    }

    private SavedDetailsKey getKey(String uid, String deviceId) {
        return SavedDetailsKey.builder().uid(uid).deviceId(deviceId).paymentGroup(WALLET).paymentCode("PAYTM_WALLET").build();
    }

    private ErrorCode handleWynkRunTimeException(WynkRuntimeException e) {
        return errorCodesCacheServiceImpl.getDefaultUnknownErrorCode(e.getErrorCode(), e.getErrorTitle());
    }

    private void handleError(ErrorCode errorCode, WynkResponseEntity.WynkResponseEntityBuilder<?> builder) {
        if (Objects.nonNull(errorCode)) {
            if (errorCode == errorCodesCacheServiceImpl.getDefaultUnknownErrorCode()) {
                builder.error(TechnicalErrorDetails.builder().code(errorCode.getInternalCode()).description(errorCode.getInternalMessage()).build()).success(false);
            } else {
                builder.error(StandardBusinessErrorDetails.builder().code(errorCode.getInternalCode()).title(errorCode.getExternalMessage()).description(errorCode.getInternalMessage()).build()).success(false);
            }
        }
    }

}