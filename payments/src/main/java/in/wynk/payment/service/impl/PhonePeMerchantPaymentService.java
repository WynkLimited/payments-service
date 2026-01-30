package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.dto.StandardBusinessErrorDetails;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.Utils;
import in.wynk.common.utils.WynkResponseUtils;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.core.dao.entity.IChargingDetails;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.phonepe.*;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.DefaultChargingRequest;
import in.wynk.payment.dto.response.AbstractCallbackResponse;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.DefaultCallbackResponse;
import in.wynk.payment.dto.response.phonepe.PhonePeChargingResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.PropertyResolverUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLDecoder;
import java.util.*;

import static in.wynk.common.constant.BaseConstants.ONE_DAY_IN_MILLI;
import static in.wynk.payment.core.constant.BeanConstant.PHONEPE_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.dto.aps.common.ApsConstant.CONTENT_TYPE;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY021;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.phonepe.PhonePeConstants.*;

@Slf4j
@Service(PHONEPE_MERCHANT_PAYMENT_SERVICE)
public class PhonePeMerchantPaymentService extends AbstractMerchantPaymentStatusService implements IMerchantPaymentChargingService<PhonePeChargingResponse, DefaultChargingRequest<?>>, IMerchantPaymentCallbackService<AbstractCallbackResponse, PhonePeCallbackRequestPayload>, IMerchantPaymentRefundService<PhonePePaymentRefundResponse, PhonePePaymentRefundRequest> {

    private static final String DEBIT_API = "/v4/debit";

    @Value("${payment.merchant.phonepe.api.base.url}")
    private String phonePeBaseUrl;
    private final Gson gson;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public PhonePeMerchantPaymentService(Gson gson,
                                         PaymentCachingService cachingService,
                                         IErrorCodesCacheService errorCodesCacheServiceImpl,
                                         ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher,
                                         @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.gson = gson;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public WynkResponseEntity<AbstractCallbackResponse> handleCallback(PhonePeCallbackRequestPayload callbackRequest) {
        handleCallbackInternal(callbackRequest);
        final Transaction transaction = TransactionContext.get();
        if (!EnumSet.of(PaymentEvent.RENEW, PaymentEvent.REFUND).contains(transaction.getType())) {
            Optional<IPurchaseDetails> optionalDetails = TransactionContext.getPurchaseDetails();
            if (optionalDetails.isPresent()) {
                final String redirectionUrl;
                IChargingDetails chargingDetails = (IChargingDetails) optionalDetails.get();
                if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                    log.warn(PaymentLoggingMarker.PHONEPE_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at phonePe end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    redirectionUrl = chargingDetails.getPageUrlDetails().getPendingPageUrl();
                } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                    log.warn(PaymentLoggingMarker.PHONEPE_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at phonePe end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    redirectionUrl = chargingDetails.getPageUrlDetails().getUnknownPageUrl();
                } else if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                    redirectionUrl = chargingDetails.getPageUrlDetails().getSuccessPageUrl();
                } else {
                    redirectionUrl = chargingDetails.getPageUrlDetails().getFailurePageUrl();
                }
                return WynkResponseUtils.redirectResponse(redirectionUrl);
            }
        }
        return WynkResponseEntity.<AbstractCallbackResponse>builder().data(DefaultCallbackResponse.builder().transactionStatus(transaction.getStatus()).build()).build();
    }

    @Override
    public PhonePeCallbackRequestPayload parseCallback(Map<String, Object> payload) {
        try {
            return objectMapper.readValue(objectMapper.writeValueAsString(payload), PhonePeCallbackRequestPayload.class);
        } catch (Exception e) {
            log.error(CALLBACK_PAYLOAD_PARSING_FAILURE, "Unable to parse callback payload due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY006, e);
        }
    }

    @Override
    public WynkResponseEntity<PhonePeChargingResponse> charge(DefaultChargingRequest<?> chargingRequest) {
        final WynkResponseEntity.WynkResponseEntityBuilder<PhonePeChargingResponse> responseBuilder = WynkResponseEntity.builder();
        try {

            final String redirectUri = getUrlFromPhonePe((IChargingDetails) chargingRequest.getPurchaseDetails());
            return responseBuilder.data(PhonePeChargingResponse.builder().redirectUrl(redirectUri).build()).build();
        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_FAILURE, e.getMessage(), e);
            return responseBuilder.error(TechnicalErrorDetails.builder().description(PAY021.getErrorMessage()).code(PAY021.getErrorCode()).build()).success(false).build();
        }
    }

    private String getUrlFromPhonePe(IChargingDetails chargingDetails) {
        final Transaction transaction = TransactionContext.get();
        final double amount = transaction.getAmount();
        final String merchantId = PropertyResolverUtils.resolve(transaction.getClientAlias(),PHONEPE_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_ID);
        PhonePePaymentRequest phonePePaymentRequest = PhonePePaymentRequest.builder().amount(Double.valueOf(amount * 100).longValue()).merchantId(merchantId).merchantUserId(transaction.getUid()).transactionId(transaction.getIdStr()).build();
        return getRedirectionUri(chargingDetails, phonePePaymentRequest,transaction.getClientAlias()).toString();
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        Transaction transaction = TransactionContext.get();
        ChargingStatusResponse chargingStatus = getStatusFromPhonePe(transaction);
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(chargingStatus).build();
    }

    private void fetchAndUpdateTransactionFromSource(Transaction transaction) {
        TransactionStatus finalTransactionStatus;
        PhonePeResponse<PhonePeTransactionResponseWrapper> response = getTransactionStatus(transaction);
        if (response.isSuccess()) {
            PhonePeStatusEnum statusCode = response.getCode();
            if (statusCode == PhonePeStatusEnum.PAYMENT_SUCCESS) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3 &&
                    statusCode == PhonePeStatusEnum.PAYMENT_PENDING) {
                finalTransactionStatus = TransactionStatus.INPROGRESS;
            } else {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } else {
            finalTransactionStatus = TransactionStatus.FAILURE;
        }

        if (finalTransactionStatus == TransactionStatus.FAILURE) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(response.getCode().name()).description(response.getMessage()).build());
        }

        transaction.setStatus(finalTransactionStatus.name());
    }

    private ChargingStatusResponse getStatusFromPhonePe(Transaction transaction) {
        this.fetchAndUpdateTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.warn(PHONEPE_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY018, "Transaction is still pending at phonepe");
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.warn(PHONEPE_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY019, PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE);
        }

        return ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build();
    }

    private void handleCallbackInternal(PhonePeCallbackRequestPayload callbackRequest) {
        final Transaction transaction = TransactionContext.get();
        try {
            Boolean validChecksum = validateChecksum(transaction.getClientAlias(),callbackRequest);
            if (validChecksum) {
                this.fetchAndUpdateTransactionFromSource(transaction);
            } else {
                log.error(PHONEPE_CHARGING_CALLBACK_FAILURE, "Invalid checksum found with Wynk transactionId: {} and uid: {}", transaction.getIdStr(), transaction.getUid());
                throw new PaymentRuntimeException(PaymentErrorType.PAY302, "Invalid checksum found for transactionId:" + transaction.getIdStr());
            }
        } catch (PaymentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentRuntimeException(PHONEPE_CHARGING_CALLBACK_FAILURE, e.getMessage(), e);
        }
    }

    private URI getRedirectionUri(IChargingDetails chargingDetails, PhonePePaymentRequest phonePePaymentRequest,String client) {
        try {
            final String salt = PropertyResolverUtils.resolve(client,PHONEPE_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_SECRET);
            String requestJson = gson.toJson(phonePePaymentRequest);
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
            String xVerifyHeader = Utils.encodeBase64(requestJson) + DEBIT_API + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + "###1";
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.add(X_REDIRECT_URL, chargingDetails.getCallbackDetails().getCallbackUrl());
            headers.add(X_REDIRECT_MODE, HttpMethod.POST.name());
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestMap, headers);
            ResponseEntity<PhonePeResponse<PhonePeChargingResponseWrapper>> response = restTemplate.exchange(phonePeBaseUrl + DEBIT_API, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<PhonePeResponse<PhonePeChargingResponseWrapper>>() {
            });
            if (response.getBody() != null && response.getBody().isSuccess()) {
                return new URI(response.getBody().getData().getRedirectURL());
            } else {
                throw new WynkRuntimeException(PaymentErrorType.PAY002);
            }
        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_CHARGING_FAILURE, "Error from phonepe: {}", hex.getResponseBodyAsString(), hex);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, hex, "Error from phonepe - " + hex.getStatusCode());
        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_FAILURE, "Error requesting URL from phonepe");
            throw new WynkRuntimeException(PHONEPE_CHARGING_FAILURE, e.getMessage(), e);
        }
    }

    private PhonePeResponse<PhonePeTransactionResponseWrapper> getTransactionStatus(Transaction txn) {
        final String salt = PropertyResolverUtils.resolve(txn.getClientAlias(),PHONEPE_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_SECRET);
        final String merchantId = PropertyResolverUtils.resolve(txn.getClientAlias(),PHONEPE_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_ID);
        Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(txn.getIdStr());
        try {
            String prefixStatusApi = "/v3/transaction/" + merchantId + "/";
            String suffixStatusApi = "/status";
            String apiPath = prefixStatusApi + txn.getIdStr() + suffixStatusApi;
            String xVerifyHeader = apiPath + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + "###1";
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Void> entity = new HttpEntity<>(headers);
            merchantTransactionEventBuilder.request(entity);
            ResponseEntity<PhonePeResponse<PhonePeTransactionResponseWrapper>> responseEntity = restTemplate.exchange(phonePeBaseUrl + apiPath, HttpMethod.GET, entity, new ParameterizedTypeReference<PhonePeResponse<PhonePeTransactionResponseWrapper>>() {
            });
            PhonePeResponse<PhonePeTransactionResponseWrapper> response = responseEntity.getBody();
            if (response != null && response.getData() != null) {
                merchantTransactionEventBuilder.externalTransactionId(response.getData().getProviderReferenceId());
            }
            merchantTransactionEventBuilder.response(gson.toJson(response));
            return response;
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, "Error from phonepe: {}", e.getResponseBodyAsString(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e, "Error from PhonePe " + e.getStatusCode());
        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, "Unable to verify status from Phonepe");
            throw new WynkRuntimeException(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, e.getMessage(), e);
        } finally {
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    private Boolean validateChecksum(String client,PhonePeCallbackRequestPayload requestPayload) {
        boolean validated = false;
        try {
            final String salt = PropertyResolverUtils.resolve(client,PHONEPE_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_SECRET);
            String rawCheckSum = URLDecoder.decode(requestPayload.getCode(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getMerchantId(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getTransactionId(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getAmount(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getProviderReferenceId(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam1(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam2(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam3(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam4(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam5(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam6(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam7(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam8(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam9(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam10(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam11(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam12(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam13(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam14(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam15(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam16(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam17(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam18(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam19(), "UTF-8") +
                    URLDecoder.decode(requestPayload.getParam20(), "UTF-8");
            final String calculatedChecksum = DigestUtils.sha256Hex(rawCheckSum + salt) + "###1";
            validated = calculatedChecksum.equals(requestPayload.getChecksum());
        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_CALLBACK_FAILURE, "Exception while Checksum validation", e);
        }
        return validated;
    }

    @Override
    public WynkResponseEntity<PhonePePaymentRefundResponse> refund(PhonePePaymentRefundRequest refundRequest) {
        Transaction refundTransaction = TransactionContext.get();
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        Builder merchantTransactionBuilder = MerchantTransactionEvent.builder(refundTransaction.getIdStr());
        WynkResponseEntity.WynkResponseEntityBuilder<PhonePePaymentRefundResponse> responseBuilder = WynkResponseEntity.builder();
        PhonePePaymentRefundResponse.PhonePePaymentRefundResponseBuilder<?, ?> refundResponseBuilder = PhonePePaymentRefundResponse.builder().transactionId(refundTransaction.getIdStr()).uid(refundTransaction.getUid()).planId(refundTransaction.getPlanId()).itemId(refundTransaction.getItemId()).clientAlias(refundTransaction.getClientAlias()).amount(refundTransaction.getAmount()).msisdn(refundTransaction.getMsisdn()).paymentEvent(refundTransaction.getType());
        try {
            final String merchantId = PropertyResolverUtils.resolve(refundTransaction.getClientAlias(),PHONEPE_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_ID);
            final String salt = PropertyResolverUtils.resolve(refundTransaction.getClientAlias(),PHONEPE_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_SECRET);
            PhonePeRefundRequest baseRefundRequest = PhonePeRefundRequest.builder().message(refundRequest.getReason()).merchantId(merchantId).amount(Double.valueOf(refundTransaction.getAmount() * 100).longValue()).providerReferenceId(refundRequest.getPpId()).transactionId(refundTransaction.getIdStr()).merchantOrderId(refundRequest.getOriginalTransactionId()).originalTransactionId(refundRequest.getOriginalTransactionId()).build();
            String requestJson = gson.toJson(baseRefundRequest);
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
            String xVerifyHeader = Utils.encodeBase64(requestJson) + REFUND_API + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + "###1";
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestMap, headers);
            merchantTransactionBuilder.request(requestEntity);
            ResponseEntity<PhonePeResponse<PhonePeRefundResponseWrapper>> response = restTemplate.exchange(phonePeBaseUrl + REFUND_API, HttpMethod.POST, requestEntity, new ParameterizedTypeReference<PhonePeResponse<PhonePeRefundResponseWrapper>>() {
            });
            merchantTransactionBuilder.response(gson.toJson(response.getBody()));
            if (response.getBody() != null && response.getBody().isSuccess()) {
                if (response.getBody().getCode() == PhonePeStatusEnum.PAYMENT_SUCCESS) {
                    finalTransactionStatus = TransactionStatus.SUCCESS;
                } else if (response.getBody().getCode() == PhonePeStatusEnum.PAYMENT_PENDING) {
                    finalTransactionStatus = TransactionStatus.INPROGRESS;
                } else {
                    PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(response.getBody().getCode().name()).description(response.getBody().getMessage()).build();
                    responseBuilder.success(false).error(StandardBusinessErrorDetails.builder().code(errorEvent.getCode()).description(errorEvent.getDescription()).build());
                    eventPublisher.publishEvent(errorEvent);
                }
            }
            if (Objects.nonNull(response.getBody()) && Objects.nonNull(response.getBody().getData()) && StringUtils.isNotEmpty(response.getBody().getData().getProviderReferenceId())) {
                merchantTransactionBuilder.externalTransactionId(response.getBody().getData().getProviderReferenceId());
                refundResponseBuilder.providerReferenceId(response.getBody().getData().getProviderReferenceId());
            }
        } catch (Exception ex) {
            PaymentErrorType errorType = PaymentErrorType.PAY020;
            PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(errorType.getErrorCode()).description(errorType.getErrorMessage()).build();
            responseBuilder.status(errorType.getHttpResponseStatusCode()).success(false).error(TechnicalErrorDetails.builder().code(errorEvent.getCode()).description(errorEvent.getDescription()).build());
            eventPublisher.publishEvent(errorEvent);
            log.error(errorType.getMarker(), ex.getMessage(), ex);
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
            eventPublisher.publishEvent(merchantTransactionBuilder.build());
        }
        return responseBuilder.data(refundResponseBuilder.build()).build();
    }

}
