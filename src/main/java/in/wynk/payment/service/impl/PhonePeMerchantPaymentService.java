package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.PlanType;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.commons.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.MerchantTransaction.MerchantTransactionBuilder;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.phonepe.PhonePePaymentRequest;
import in.wynk.payment.dto.phonepe.PhonePeTransactionResponse;
import in.wynk.payment.dto.phonepe.PhonePeTransactionStatus;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatus;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.Map;

import static in.wynk.commons.constants.Constants.*;
import static in.wynk.payment.core.constant.PaymentConstants.REQUEST;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.phonepe.PhonePeConstants.*;
import static in.wynk.queue.constant.BeanConstant.SQS_EVENT_PRODUCER;

@Slf4j
@Service(BeanConstant.PHONEPE_MERCHANT_PAYMENT_SERVICE)
public class PhonePeMerchantPaymentService implements IRenewalMerchantPaymentService {

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
    @Value("${payment.pooling.queue.reconciliation.name}")
    private String reconciliationQueue;
    @Value("${payment.pooling.queue.reconciliation.sqs.producer.delayInSecond}")
    private int reconciliationMessageDelay;

    private final Gson gson;
    private final RestTemplate restTemplate;
    private final PaymentCachingService cachingService;
    private final ISQSMessagePublisher sqsMessagePublisher;
    private final ITransactionManagerService transactionManager;


    private final String debitCall = "/v3/debit";

    public PhonePeMerchantPaymentService(Gson gson,
                                         RestTemplate restTemplate,
                                         PaymentCachingService cachingService,
                                         ITransactionManagerService transactionManager,
                                         @Qualifier(SQS_EVENT_PRODUCER) ISQSMessagePublisher sqsMessagePublisher) {
        this.gson = gson;
        this.restTemplate = restTemplate;
        this.cachingService = cachingService;
        this.transactionManager = transactionManager;
        this.sqsMessagePublisher = sqsMessagePublisher;
    }

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        URI returnUrl = processCallback(callbackRequest);
        return BaseResponse.redirectResponse(returnUrl);

    }

    @Override
    public BaseResponse<Void> doCharging(ChargingRequest chargingRequest) {
        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final String uid = sessionDTO.get(UID);
        final String msisdn = sessionDTO.get(MSISDN);

        int planId = chargingRequest.getPlanId();
        final PlanDTO selectedPlan = cachingService.getPlan(planId);

        final TransactionEvent eventType = selectedPlan.getPlanType() == PlanType.ONE_TIME_SUBSCRIPTION ? TransactionEvent.PURCHASE: TransactionEvent.SUBSCRIBE;

        try {
            final long finalPlanAmount = selectedPlan.getFinalPriceInPaise();
            final Transaction transaction = transactionManager.initiateTransaction(uid, msisdn, planId, selectedPlan.getFinalPrice(), PaymentCode.PHONEPE_WALLET, eventType);
            String redirectUri = getUrlFromPhonePe(finalPlanAmount, transaction);

            putValueInSession(SessionKeys.WYNK_TRANSACTION_ID, transaction.getIdStr());
            putValueInSession(SessionKeys.SELECTED_PLAN_ID, selectedPlan.getId());
            putValueInSession(SessionKeys.PAYMENT_CODE, PaymentCode.PHONEPE_WALLET.getCode());

            PaymentReconciliationMessage reconciliationMessage = new PaymentReconciliationMessage(transaction);
            publishSQSMessage(reconciliationQueue, reconciliationMessageDelay, reconciliationMessage);

            return BaseResponse.redirectResponse(redirectUri);

        } catch (Exception e) {
            throw new WynkRuntimeException(PHONEPE_CHARGING_FAILURE, e.getMessage(), e);
        }
    }

    private String getUrlFromPhonePe(long amount, Transaction transaction) {
        PhonePePaymentRequest phonePePaymentRequest = PhonePePaymentRequest.builder().amount(amount)
                .merchantId(merchantId).merchantUserId(transaction.getUid()).transactionId(transaction.getIdStr()).build();
        return getRedirectionUri(phonePePaymentRequest).toString();
    }

    @Override
    public BaseResponse<?> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
        return null;
    }


    @Override
    public BaseResponse<ChargingStatus> status(ChargingStatusRequest chargingStatusRequest) {
        ChargingStatus chargingStatus;
        switch (chargingStatusRequest.getMode()) {
            case SOURCE:
                chargingStatus = getStatusFromPhonePe(chargingStatusRequest);
                break;
            case LOCAL:
                chargingStatus = fetchChargingStatusFromDataSource(chargingStatusRequest);
                break;
            default:
                throw new WynkRuntimeException(PaymentErrorType.PAY008);
        }
        return BaseResponse.<ChargingStatus>builder()
                .status(HttpStatus.OK)
                .body(chargingStatus)
                .build();
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
            transaction.setMerchantTransaction(MerchantTransaction.builder()
                    .externalTransactionId(phonePeTransactionStatusResponse.getData().providerReferenceId)
                    .response(phonePeTransactionStatusResponse)
                    .build());
        } else {
            finalTransactionStatus = TransactionStatus.FAILURE;
        }

        if (finalTransactionStatus == TransactionStatus.FAILURE) {
            transaction.setPaymentError(PaymentError.builder()
                    .code(phonePeTransactionStatusResponse.getCode().name())
                    .description(phonePeTransactionStatusResponse.getMessage())
                    .build());
        }

        transaction.setStatus(finalTransactionStatus.name());
    }

    private ChargingStatus getStatusFromPhonePe(ChargingStatusRequest chargingStatusRequest) {
        Transaction transaction = transactionManager.get(chargingStatusRequest.getTransactionId());
        try {
            transactionManager.updateAndPublishAsync(transaction, this::fetchAndUpdateTransactionFromSource);

            if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                log.error(PHONEPE_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY008, "Transaction is still pending at phonepe");
            } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                log.error(PHONEPE_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at phonePe end for uid: {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                throw new WynkRuntimeException(PaymentErrorType.PAY008, PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE);
            }

            return ChargingStatus.builder().transactionStatus(transaction.getStatus()).build();
        } finally {
            transactionManager.upsert(transaction);
        }

    }

    private ChargingStatus fetchChargingStatusFromDataSource(ChargingStatusRequest chargingStatusRequest) {
        Transaction transaction = transactionManager.get(chargingStatusRequest.getTransactionId());
        return ChargingStatus.builder()
                .transactionStatus(transaction.getStatus())
                .build();
    }

    private URI processCallback(CallbackRequest callbackRequest) {
        final String transactionId = getValueFromSession(SessionKeys.WYNK_TRANSACTION_ID);
        final Transaction transaction = transactionManager.get(transactionId);
        try {
            Map<String, String> requestPayload = (Map<String, String>) callbackRequest.getBody();
            PhonePeTransactionResponse phonePeTransactionResponse = new PhonePeTransactionResponse(requestPayload);

            String errorCode = phonePeTransactionResponse.getCode().name();
            String errorMessage = phonePeTransactionResponse.getMessage();

            Boolean validChecksum = validateChecksum(requestPayload);
            if (validChecksum && phonePeTransactionResponse.getCode() != null) {
                transactionManager.updateAndPublishSync(transaction, this::fetchAndUpdateTransactionFromSource);
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
                        transactionId,
                        phonePeTransactionResponse.getData().getProviderReferenceId(),
                        errorCode,
                        errorMessage,
                        transaction.getUid());
                throw new PaymentRuntimeException(PaymentErrorType.PAY302, "Invalid checksum found for transactionId:" + transactionId);
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
            String xVerifyHeader = Utils.encodeBase64(requestJson) + debitCall + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + "###1";
            HttpHeaders headers = new HttpHeaders();
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.add(X_REDIRECT_URL, phonePeCallBackURL + "?sid=" + SessionContextHolder.getId());
            headers.add(X_REDIRECT_MODE, HttpMethod.POST.name());
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(requestMap, headers);
            URI uri = restTemplate.postForLocation(phonePeBaseUrl + debitCall, requestEntity);
            return new URI(phonePeBaseUrl + uri);
        } catch (HttpStatusCodeException hex) {
            AnalyticService.update(PHONE_STATUS_CODE, hex.getRawStatusCode());
            log.error(PHONEPE_CHARGING_FAILURE, "Error from phonepe: {}", hex.getResponseBodyAsString(), hex);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, hex, "Error from phonepe - " +hex.getStatusCode().toString());
        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_FAILURE, "Error requesting URL from phonepe");
            throw new WynkRuntimeException(PHONEPE_CHARGING_FAILURE, e.getMessage(), e);
        }
    }

    private PhonePeTransactionResponse getTransactionStatus(Transaction txn) {
        MerchantTransactionBuilder merchantTxnBuilder = MerchantTransaction.builder();
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
            merchantTxnBuilder.request(entity);
            ResponseEntity<PhonePeTransactionResponse> responseEntity = restTemplate.exchange(phonePeBaseUrl + apiPath, HttpMethod.GET, entity, PhonePeTransactionResponse.class, new HashMap<>());
            PhonePeTransactionResponse phonePeTransactionResponse = responseEntity.getBody();
            if (phonePeTransactionResponse != null && phonePeTransactionResponse.getCode() != null) {
                log.info("PhonePe txn response for transaction Id {} :: {}", txn.getIdStr(), phonePeTransactionResponse);
            }
            return phonePeTransactionResponse;
        }catch (HttpStatusCodeException hex){
            merchantTxnBuilder.response(hex.getResponseBodyAsString());
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, "Error from phonepe: {}", hex.getResponseBodyAsString(), hex);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, hex, "Error from PhonePe - " + hex.getStatusCode().toString());
        } catch (Exception e) {
            log.error(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, "Unable to verify status from Phonepe");
            throw new WynkRuntimeException(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, e.getMessage(), e);
        } finally {
            txn.setMerchantTransaction(merchantTxnBuilder.build());
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

    private <T> T getValueFromSession(String key) {
        Session<SessionDTO> session = SessionContextHolder.get();
        return session.getBody().get(key);
    }

    private <T> void putValueInSession(String key, T value) {
        Session<SessionDTO> session = SessionContextHolder.get();
        session.getBody().put(key, value);
    }

    private <T> void publishSQSMessage(String queueName, int messageDelay, T message) {
        try {
            sqsMessagePublisher.publish(SendSQSMessageRequest.<T>builder()
                    .queueName(queueName)
                    .delaySeconds(messageDelay)
                    .message(message)
                    .build());
        } catch (Exception e) {
            throw new WynkRuntimeException(QueueErrorType.SQS001, e);
        }
    }
}
