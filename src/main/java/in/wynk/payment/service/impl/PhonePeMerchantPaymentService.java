package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
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
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.phonepe.PhonePeChargingResponse;
import in.wynk.payment.dto.phonepe.PhonePePaymentRequest;
import in.wynk.payment.dto.phonepe.PhonePeTransactionResponse;
import in.wynk.payment.dto.phonepe.PhonePeTransactionStatus;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.session.context.SessionContextHolder;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import static in.wynk.common.constant.BaseConstants.ONE_DAY_IN_MILLI;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.phonepe.PhonePeConstants.*;

@Slf4j
@Service(BeanConstant.PHONEPE_MERCHANT_PAYMENT_SERVICE)
public class PhonePeMerchantPaymentService implements IRenewalMerchantPaymentService {

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
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final RestTemplate restTemplate;

    public PhonePeMerchantPaymentService(Gson gson,
                                         PaymentCachingService cachingService, ApplicationEventPublisher eventPublisher,
                                         @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate) {
        this.gson = gson;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.restTemplate = restTemplate;
    }

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        URI returnUrl = processCallback(callbackRequest);
        return BaseResponse.redirectResponse(returnUrl);

    }

    @Override
    public BaseResponse<Void> doCharging(ChargingRequest chargingRequest) {
        final Transaction transaction = TransactionContext.get();
        try {
            final double finalPlanAmount = transaction.getAmount();
            final String redirectUri = getUrlFromPhonePe(finalPlanAmount, transaction);
            return BaseResponse.redirectResponse(redirectUri);
        } catch (Exception e) {
            throw new WynkRuntimeException(PHONEPE_CHARGING_FAILURE, e.getMessage(), e);
        }
    }

    private String getUrlFromPhonePe(double amount, Transaction transaction) {
        PhonePePaymentRequest phonePePaymentRequest = PhonePePaymentRequest.builder().amount(Double.valueOf(amount * 100).longValue()).merchantId(merchantId).merchantUserId(transaction.getUid()).transactionId(transaction.getIdStr()).build();
        return getRedirectionUri(phonePePaymentRequest).toString();
    }

    @Override
    public void doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
    }


    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionStatusRequest transactionStatusRequest) {
        ChargingStatusResponse chargingStatus;
        Transaction transaction = TransactionContext.get();
        switch (transactionStatusRequest.getMode()) {
            case SOURCE:
                chargingStatus = getStatusFromPhonePe(transaction);
                break;
            case LOCAL:
                chargingStatus = fetchChargingStatusFromDataSource(transaction);
                break;
            default:
                throw new WynkRuntimeException(PaymentErrorType.PAY008);
        }
        return BaseResponse.<ChargingStatusResponse>builder().status(HttpStatus.OK).body(chargingStatus).build();
    }

    private void fetchAndUpdateTransactionFromSource(Transaction transaction) {
        TransactionStatus finalTransactionStatus;
        PhonePeTransactionResponse phonePeTransactionStatusResponse = getTransactionStatus(transaction);
        if (phonePeTransactionStatusResponse.getSuccess()) {
            PhonePeTransactionStatus statusCode = phonePeTransactionStatusResponse.getCode();
            if (statusCode == PhonePeTransactionStatus.PAYMENT_SUCCESS) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3 &&
                    statusCode == PhonePeTransactionStatus.PAYMENT_PENDING) {
                finalTransactionStatus = TransactionStatus.INPROGRESS;
            } else {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } else {
            finalTransactionStatus = TransactionStatus.FAILURE;
        }

        if (finalTransactionStatus == TransactionStatus.FAILURE) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(phonePeTransactionStatusResponse.getCode().name()).description(phonePeTransactionStatusResponse.getMessage()).build());
        }

        transaction.setStatus(finalTransactionStatus.name());
    }

    private ChargingStatusResponse getStatusFromPhonePe(Transaction transaction) {
        this.fetchAndUpdateTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY008, "Transaction is still pending at phonepe");
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY008, PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE);
        }

        return ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build();
    }

    private ChargingStatusResponse fetchChargingStatusFromDataSource(Transaction transaction) {
        return ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).validity(cachingService.validTillDate(transaction.getPlanId())).build();
    }

    private URI processCallback(CallbackRequest callbackRequest) {
        final Transaction transaction = TransactionContext.get();
        try {
            Map<String, String> requestPayload = (Map<String, String>) callbackRequest.getBody();
            PhonePeTransactionResponse phonePeTransactionResponse = new PhonePeTransactionResponse(requestPayload);

            String errorCode = phonePeTransactionResponse.getCode().name();
            String errorMessage = phonePeTransactionResponse.getMessage();

            Boolean validChecksum = validateChecksum(requestPayload);
            if (validChecksum && phonePeTransactionResponse.getCode() != null) {
                this.fetchAndUpdateTransactionFromSource(transaction);
                if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                    log.error(PaymentLoggingMarker.PHONEPE_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at phonePe end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY300);
                } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                    log.error(PaymentLoggingMarker.PHONEPE_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at phonePe end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY301);
                } else if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {
                    return new URIBuilder(SUCCESS_PAGE + SessionContextHolder.getId()).build();
                } else {
                    throw new PaymentRuntimeException(PaymentErrorType.PAY302);
                }
            } else {
                log.error(PHONEPE_CHARGING_CALLBACK_FAILURE,
                        "Invalid checksum found with Wynk transactionId: {}, PhonePe transactionId: {}, Reason: error code: {}, error message: {} for uid: {}",
                        transaction.getIdStr(),
                        phonePeTransactionResponse.getData().getProviderReferenceId(),
                        errorCode,
                        errorMessage,
                        transaction.getUid());
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
            ResponseEntity<PhonePeChargingResponse> response = restTemplate.postForEntity(phonePeBaseUrl + DEBIT_API, requestEntity, PhonePeChargingResponse.class);
            if (response.getBody() != null && response.getBody().isSuccess()) {
                return new URI(response.getBody().getData().getRedirectURL());
            } else {
                throw new WynkRuntimeException(PaymentErrorType.PAY008, response.getBody().getMessage());
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

    private PhonePeTransactionResponse getTransactionStatus(Transaction txn) {
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
            ResponseEntity<PhonePeTransactionResponse> responseEntity = restTemplate.exchange(phonePeBaseUrl + apiPath, HttpMethod.GET, entity, PhonePeTransactionResponse.class, new HashMap<>());
            PhonePeTransactionResponse phonePeTransactionResponse = responseEntity.getBody();
            if (phonePeTransactionResponse != null && phonePeTransactionResponse.getCode() != null) {
                log.info("PhonePe txn response for transaction Id {} :: {}", txn.getIdStr(), phonePeTransactionResponse);
            }
            if (phonePeTransactionResponse.getData() != null)
                merchantTransactionEventBuilder.externalTransactionId(phonePeTransactionResponse.getData().providerReferenceId);
            merchantTransactionEventBuilder.response(phonePeTransactionResponse);
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
            return phonePeTransactionResponse;
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

}
