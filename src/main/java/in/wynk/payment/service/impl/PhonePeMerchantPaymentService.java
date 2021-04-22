package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.PhonePePaymentRefundRequest;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.phonepe.*;
import in.wynk.payment.dto.request.AbstractPaymentRefundRequest;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.AbstractMerchantPaymentStatusService;
import in.wynk.payment.service.IMerchantPaymentRefundService;
import in.wynk.payment.service.IOTCMerchantPaymentService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.context.SessionContextHolder;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.phonepe.PhonePeConstants.*;

@Slf4j
@Service(BeanConstant.PHONEPE_MERCHANT_PAYMENT_SERVICE)
public class PhonePeMerchantPaymentService extends AbstractMerchantPaymentStatusService implements IOTCMerchantPaymentService, IMerchantPaymentRefundService {

    private static final String DEBIT_API = "/v4/debit";

    @Value("${payment.merchant.phonepe.id}")
    private String merchantId;
    @Value("${payment.merchant.phonepe.callback.url}")
    private String phonePeCallBackURL;
    @Value("${payment.merchant.phonepe.api.base.url}")
    private String phonePeBaseUrl;
    @Value("${payment.merchant.phonepe.salt}")
    private String salt;
    @Value("${payment.success.page}")
    private String SUCCESS_PAGE;

    private final Gson gson;
    private final RestTemplate restTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public PhonePeMerchantPaymentService(Gson gson,
                                         PaymentCachingService cachingService,
                                         ApplicationEventPublisher eventPublisher,
                                         @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate) {
        super(cachingService);
        this.gson = gson;
        this.restTemplate = restTemplate;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        String returnUrl = processCallback(callbackRequest);
        return BaseResponse.redirectResponse(returnUrl);

    }

    @Override
    public BaseResponse<Map<String, String>> doCharging(ChargingRequest chargingRequest) {
        final Transaction transaction = TransactionContext.get();
        try {
            final double finalPlanAmount = transaction.getAmount();
            final String redirectUri = getUrlFromPhonePe(finalPlanAmount, transaction);
            Map<String, String> response = new HashMap<>();
            response.put(REDIRECTION_URL, redirectUri);
            return BaseResponse.<Map<String, String>>builder().body(response).status(HttpStatus.OK).build();
        } catch (Exception e) {
            throw new WynkRuntimeException(PHONEPE_CHARGING_FAILURE, e.getMessage(), e);
        }
    }

    private String getUrlFromPhonePe(double amount, Transaction transaction) {
        PhonePePaymentRequest phonePePaymentRequest = PhonePePaymentRequest.builder().amount(Double.valueOf(amount * 100).longValue()).merchantId(merchantId).merchantUserId(transaction.getUid()).transactionId(transaction.getIdStr()).build();
        return getRedirectionUri(phonePePaymentRequest).toString();
    }

    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        Transaction transaction = TransactionContext.get();
        ChargingStatusResponse chargingStatus = getStatusFromPhonePe(transaction);
        return BaseResponse.<ChargingStatusResponse>builder().status(HttpStatus.OK).body(chargingStatus).build();
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
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY018, "Transaction is still pending at phonepe");
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY019, PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE);
        }

        return ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build();
    }

    private String processCallback(CallbackRequest callbackRequest) {
        final Transaction transaction = TransactionContext.get();
        try {
            Map<String, String> requestPayload = (Map<String, String>) callbackRequest.getBody();
            Boolean validChecksum = validateChecksum(requestPayload);
            if (validChecksum) {
                this.fetchAndUpdateTransactionFromSource(transaction);
                if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                    log.error(PaymentLoggingMarker.PHONEPE_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at phonePe end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY300);
                } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                    log.error(PaymentLoggingMarker.PHONEPE_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at phonePe end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY301);
                } else if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {
                    SessionDTO sessionDTO = SessionContextHolder.getBody();
                    return SUCCESS_PAGE + SessionContextHolder.getId() +
                            SLASH +
                            sessionDTO.<String>get(OS) +
                            QUESTION_MARK +
                            SERVICE +
                            EQUAL +
                            sessionDTO.<String>get(SERVICE) +
                            AND +
                            BUILD_NO +
                            EQUAL +
                            sessionDTO.<Integer>get(BUILD_NO);
                } else {
                    throw new PaymentRuntimeException(PaymentErrorType.PAY302);
                }
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

    private URI getRedirectionUri(PhonePePaymentRequest phonePePaymentRequest) {
        try {
            String requestJson = gson.toJson(phonePePaymentRequest);
            Map<String, String> requestMap = new HashMap<>();
            requestMap.put(REQUEST, Utils.encodeBase64(requestJson));
            String xVerifyHeader = Utils.encodeBase64(requestJson) + DEBIT_API + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + "###1";
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.add(X_REDIRECT_URL, phonePeCallBackURL + SessionContextHolder.getId());
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
            throw new WynkRuntimeException(PaymentErrorType.PAY998, hex, "Error from phonepe - " + hex.getStatusCode().toString());
        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_FAILURE, "Error requesting URL from phonepe");
            throw new WynkRuntimeException(PHONEPE_CHARGING_FAILURE, e.getMessage(), e);
        }
    }

    private PhonePeResponse<PhonePeTransactionResponseWrapper> getTransactionStatus(Transaction txn) {
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
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e, "Error from PhonePe " + e.getStatusCode().toString());
        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, "Unable to verify status from Phonepe");
            throw new WynkRuntimeException(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, e.getMessage(), e);
        } finally {
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    private Boolean validateChecksum(Map<String, String> requestParams) {
        String checksum = StringUtils.EMPTY;
        boolean validated = false;
        StringBuilder validationString = new StringBuilder();
        try {
            for (String key : requestParams.keySet()) {
                if (!key.equals("checksum") && !key.equals("tid")) {
                    validationString.append(URLDecoder.decode(requestParams.get(key), "UTF-8"));
                } else if (key.equals("checksum")) {
                    checksum = URLDecoder.decode(requestParams.get(key), "UTF-8");
                }
            }
            String calculatedChecksum = DigestUtils.sha256Hex(validationString + salt) + "###1";
            if (StringUtils.equals(checksum, calculatedChecksum)) {
                validated = true;
            }

        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_CALLBACK_FAILURE, "Exception while Checksum validation");
        }
        return validated;
    }

    @Override
    public BaseResponse<?> refund(AbstractPaymentRefundRequest request) {
        Transaction refundTransaction = TransactionContext.get();
        TransactionStatus finalTransactionStatus = TransactionStatus.FAILURE;
        Builder merchantTransactionBuilder = MerchantTransactionEvent.builder(refundTransaction.getIdStr());
        PhonePePaymentRefundResponse.PhonePePaymentRefundResponseBuilder<?, ?> refundResponseBuilder = PhonePePaymentRefundResponse.builder().transactionId(refundTransaction.getIdStr()).uid(refundTransaction.getUid()).planId(refundTransaction.getPlanId()).itemId(refundTransaction.getItemId()).clientAlias(refundTransaction.getClientAlias()).amount(refundTransaction.getAmount()).msisdn(refundTransaction.getMsisdn()).paymentEvent(refundTransaction.getType());
        try {
            PhonePePaymentRefundRequest refundRequest = (PhonePePaymentRefundRequest) request;
            PhonePeRefundRequest baseRefundRequest = PhonePeRefundRequest.builder().message(request.getReason()).merchantId(merchantId).amount(Double.valueOf(refundTransaction.getAmount() * 100).longValue()).providerReferenceId(refundRequest.getPpId()).transactionId(refundTransaction.getIdStr()).merchantOrderId(refundRequest.getOriginalTransactionId()).originalTransactionId(refundRequest.getOriginalTransactionId()).build();
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
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(response.getBody().getCode().name()).description(response.getBody().getMessage()).build());
                }
            }
            if (Objects.nonNull(response.getBody()) && Objects.nonNull(response.getBody().getData()) && StringUtils.isNotEmpty(response.getBody().getData().getProviderReferenceId())) {
                merchantTransactionBuilder.externalTransactionId(response.getBody().getData().getProviderReferenceId());
                refundResponseBuilder.providerReferenceId(response.getBody().getData().getProviderReferenceId());
            }
        } catch (Exception ex) {
            throw new WynkRuntimeException(PaymentErrorType.PAY020, ex, ex.getMessage());
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
            eventPublisher.publishEvent(merchantTransactionBuilder.build());
        }
        return BaseResponse.builder().body(refundResponseBuilder.build()).status(HttpStatus.OK).build();
    }

}
