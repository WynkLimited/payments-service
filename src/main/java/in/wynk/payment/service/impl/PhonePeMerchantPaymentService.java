package in.wynk.payment.service.impl;

import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.gson.Gson;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.commons.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.MerchantTransaction.MerchantTransactionBuilder;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.phonepe.PhonePePaymentRequest;
import in.wynk.payment.dto.phonepe.PhonePeTransactionResponse;
import in.wynk.payment.dto.phonepe.PhonePeTransactionStatus;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatus;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.net.URLDecoder;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;

import static in.wynk.commons.constants.Constants.MSISDN;
import static in.wynk.commons.constants.Constants.ONE_DAY_IN_MILLI;
import static in.wynk.commons.constants.Constants.SERVICE;
import static in.wynk.commons.constants.Constants.UID;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PHONEPE_CHARGING_CALLBACK_FAILURE;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PHONEPE_CHARGING_FAILURE;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PHONEPE_CHARGING_STATUS_VERIFICATION;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE;
import static in.wynk.queue.constant.BeanConstant.SQS_EVENT_PRODUCER;

@Service(BeanConstant.PHONEPE_MERCHANT_PAYMENT_SERVICE)
public class PhonePeMerchantPaymentService implements IRenewalMerchantPaymentService {

    private final RestTemplate restTemplate;
    private final ISQSMessagePublisher sqsMessagePublisher;
    private final ITransactionManagerService transactionManager;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final PaymentCachingService cachingService;

    @Value("${payment.merchant.phonepe.id}")
    private String merchantId;
    @Value("${payment.merchant.phonepe.callback.url}")
    private String phonePeCallBackURL;
    @Value("${payment.merchant.phonepe.api.base.url}")
    private String phonePeBaseUrl;
    @Value("${payment.merchant.phonepe.salt}")
    private String salt;
    @Value("${payment.merchant.phonepe.return.wynkurl}")
    private String returnWynkUrl;
    @Value("${payment.pooling.queue.reconciliation.name}")
    private String reconciliationQueue;
    @Value("${payment.pooling.queue.subscription.name}")
    private String subscriptionQueue;
    @Value("${payment.pooling.queue.reconciliation.sqs.producer.delayInSecond}")
    private int reconciliationMessageDelay;
    @Value("${payment.pooling.queue.subscription.sqs.producer.delayInSecond}")
    private int subscriptionMessageDelay;
    @Autowired
    private Gson gson;


    private final String debitCall = "/v3/debit";


    private Logger logger = LoggerFactory.getLogger(PhonePeMerchantPaymentService.class.getCanonicalName());

    public PhonePeMerchantPaymentService(RestTemplate restTemplate,
                                         ITransactionManagerService transactionManager,
                                         @Qualifier(SQS_EVENT_PRODUCER) ISQSMessagePublisher sqsMessagePublisher, ISubscriptionServiceManager subscriptionServiceManager, PaymentCachingService cachingService) {
        this.restTemplate = restTemplate;
        this.transactionManager = transactionManager;
        this.sqsMessagePublisher = sqsMessagePublisher;
        this.subscriptionServiceManager = subscriptionServiceManager;
        this.cachingService = cachingService;
    }

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        URI returnUrl = processCallback(callbackRequest);
        return BaseResponse.redirectResponse(returnUrl);

    }

    @Override
    public BaseResponse<Void> doCharging(ChargingRequest chargingRequest) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        String uid = sessionDTO.get(UID);
        String msisdn = sessionDTO.get(MSISDN);
        String wynkService = sessionDTO.get(SERVICE);
        try {
            int planId = chargingRequest.getPlanId();
            final PlanDTO selectedPlan = cachingService.getPlan(planId);
            final long finalPlanAmount = selectedPlan.getFinalPriceInPaise();
            final Transaction transaction = transactionManager.initiateTransaction(uid, msisdn, planId, selectedPlan.getFinalPrice(), PaymentCode.PHONEPE_WALLET, wynkService);
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
                transaction.setExitTime(Calendar.getInstance());
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
            transaction.setExitTime(Calendar.getInstance());
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
        TransactionStatus existingTransactionStatus;
        TransactionStatus finalTransactionStatus;

        Transaction transaction = transactionManager.get(chargingStatusRequest.getTransactionId());

        existingTransactionStatus = transaction.getStatus();
        fetchAndUpdateTransactionFromSource(transaction);
        finalTransactionStatus = transaction.getStatus();

        if (existingTransactionStatus != TransactionStatus.SUCCESS && finalTransactionStatus == TransactionStatus.SUCCESS) {
            subscriptionServiceManager.publish(chargingStatusRequest.getPlanId(),
                    chargingStatusRequest.getUid(),
                    chargingStatusRequest.getTransactionId(),
                    finalTransactionStatus,
                    chargingStatusRequest.getTransactionEvent());
        } else if (existingTransactionStatus == TransactionStatus.SUCCESS && finalTransactionStatus == TransactionStatus.FAILURE) {
            subscriptionServiceManager.publish(chargingStatusRequest.getPlanId(),
                    chargingStatusRequest.getUid(),
                    chargingStatusRequest.getTransactionId(),
                    finalTransactionStatus,
                    TransactionEvent.UNSUBSCRIBE);
        }

        if (finalTransactionStatus == TransactionStatus.INPROGRESS) {
            logger.error(PHONEPE_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at phonePe end for transactionId {}", chargingStatusRequest.getTransactionId());
            throw new WynkRuntimeException(PaymentErrorType.PAY013);
        } else if (finalTransactionStatus == TransactionStatus.UNKNOWN) {
            logger.error(PHONEPE_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status from phonePe end for transactionId {}", chargingStatusRequest.getTransactionId());
            throw new WynkRuntimeException(PaymentErrorType.PAY014);
        }

        return ChargingStatus.builder()
                .transactionStatus(finalTransactionStatus)
                .build();
    }

    private ChargingStatus fetchChargingStatusFromDataSource(ChargingStatusRequest chargingStatusRequest) {
        String transactionId = getValueFromSession(SessionKeys.WYNK_TRANSACTION_ID);
        Transaction transaction = transactionManager.get(transactionId);
        return ChargingStatus.builder()
                .transactionStatus(transaction.getStatus())
                .build();
    }

    private URI processCallback(CallbackRequest callbackRequest) {
        final String transactionId = getValueFromSession(SessionKeys.WYNK_TRANSACTION_ID);
        final Transaction transaction = transactionManager.get(transactionId);

        try {
            Map<String, String> requestPayload = (Map<String, String>) callbackRequest.getBody();
            String uid = getValueFromSession(UID);
            PlanDTO selectedPlan = cachingService.getPlan(getValueFromSession(SessionKeys.SELECTED_PLAN_ID));

            PhonePeTransactionResponse phonePeTransactionResponse = new PhonePeTransactionResponse(requestPayload);

            String errorCode = phonePeTransactionResponse.getCode().name();
            String errorMessage = phonePeTransactionResponse.getMessage();

            Boolean validChecksum = validateChecksum(requestPayload);
            if (validChecksum && phonePeTransactionResponse.getCode() != null) {
                fetchAndUpdateTransactionFromSource(transaction);

                if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                    transaction.setExitTime(Calendar.getInstance());
                    subscriptionServiceManager.publish(selectedPlan.getId(), uid, transactionId, transaction.getStatus(), transaction.getType());
                }
            } else {
                logger.error(PHONEPE_CHARGING_CALLBACK_FAILURE,
                        "Invalid checksum found with Wynk transactionId: {}, PhonePe transactionId: {}, Reason: error code: {}, error message: {} for uid: {}",
                        transactionId,
                        phonePeTransactionResponse.getData().getProviderReferenceId(),
                        errorCode,
                        errorMessage,
                        uid);
            }

            URIBuilder returnUrl = new URIBuilder(phonePeCallBackURL).addParameter(TXN_ID, transactionId)
                    .addParameter(SESSION_ID, SessionContextHolder.getId());
            return returnUrl.build();
        } catch (Exception e) {
            throw new WynkRuntimeException(PHONEPE_CHARGING_CALLBACK_FAILURE, e.getMessage(), e);
        } finally {
            transactionManager.upsert(transaction);
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
            logger.error(PHONEPE_CHARGING_FAILURE, "Error from phonepe: {}", hex.getResponseBodyAsString(), hex);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, hex, "Error from phonepe - " +hex.getStatusCode().toString());
        } catch (Exception e) {
            logger.error(PHONEPE_CHARGING_FAILURE, "Error requesting URL from phonepe");
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
                logger.info("PhonePe txn response for transaction Id {} :: {}", txn.getIdStr(), phonePeTransactionResponse);
            }
            return phonePeTransactionResponse;
        }catch (HttpStatusCodeException hex){
            merchantTxnBuilder.response(hex.getResponseBodyAsString());
            logger.error(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, "Error from phonepe: {}", hex.getResponseBodyAsString(), hex);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, hex, "Error from PhonePe - " + hex.getStatusCode().toString());
        } catch (Exception e) {
            logger.error(PHONEPE_CHARGING_STATUS_VERIFICATION_FAILURE, "Unable to verify status from Phonepe");
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
            logger.error(PHONEPE_CHARGING_CALLBACK_FAILURE, "Exception while Checksum validation");
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
