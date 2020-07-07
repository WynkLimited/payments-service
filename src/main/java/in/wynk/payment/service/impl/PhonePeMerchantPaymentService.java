package in.wynk.payment.service.impl;

import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.JsonObject;
import java.net.URI;
import java.net.URLDecoder;
import java.time.Duration;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.DiscountDTO;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.dto.SubscriptionNotificationMessage;
import in.wynk.commons.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.*;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.phonepe.PhonePeTransactionStatus;
import in.wynk.payment.dto.phonepe.PhonePePaymentRequest;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.phonepe.PhonePeTransactionResponse;
import in.wynk.payment.dto.response.ChargingStatus;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.revenue.commons.PlanType;
import in.wynk.revenue.commons.TransactionEvent;
import in.wynk.revenue.commons.TransactionStatus;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.revenue.commons.Constants.ONE_DAY_IN_MILLI;

@Service(BeanConstant.PHONEPE_MERCHANT_PAYMENT_SERVICE)
public class PhonePeMerchantPaymentService implements IRenewalMerchantPaymentService {

    private final RestTemplate restTemplate;
    private final ISQSMessagePublisher sqsMessagePublisher;
    private final ITransactionManagerService transactionManager;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final RateLimiter rateLimiter = RateLimiter.create(6.0);

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


    private final String debitCall = "/v3/debit";


    private Logger logger = LoggerFactory.getLogger(PhonePeMerchantPaymentService.class.getCanonicalName());

    public PhonePeMerchantPaymentService(RestTemplate restTemplate,
                                         ITransactionManagerService transactionManager,
                                         IRecurringPaymentManagerService recurringPaymentManagerService,
                                         @Qualifier(in.wynk.queue.constant.BeanConstant.SQS_EVENT_PRODUCER) ISQSMessagePublisher sqsMessagePublisher, ISubscriptionServiceManager subscriptionServiceManager) {
        this.restTemplate = restTemplate;
        this.transactionManager = transactionManager;
        this.sqsMessagePublisher = sqsMessagePublisher;
        this.recurringPaymentManagerService = recurringPaymentManagerService;
        this.subscriptionServiceManager = subscriptionServiceManager;
    }

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        URI returnUrl = processCallback(callbackRequest);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(HttpHeaders.LOCATION, returnUrl.toString());
        httpHeaders.add(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(Duration.ZERO).mustRevalidate().getHeaderValue());
        httpHeaders.add(HttpHeaders.PRAGMA, CacheControl.noCache().getHeaderValue());
        httpHeaders.add(HttpHeaders.EXPIRES, String.valueOf(0));
        return BaseResponse.<Void>builder()
                .status(HttpStatus.FOUND)
                .headers(httpHeaders)
                .build();

    }

    @Override
    public BaseResponse<String> doCharging(ChargingRequest chargingRequest) {
        try {
            int planId = chargingRequest.getPlanId();
            final PlanDTO selectedPlan = getSelectedPlan(planId);
            final float finalPlanAmount = getFinalPlanAmountToBePaid(selectedPlan);
            final Transaction transaction = initialiseTransaction(chargingRequest, finalPlanAmount);
            final String uid = getValueFromSession(SessionKeys.UID);
            PhonePePaymentRequest phonePePaymentRequest =
                    PhonePePaymentRequest.builder()
                        .amount((long) finalPlanAmount*100)
                        .merchantId(merchantId)
                        .merchantUserId(uid)
                        .transactionId(transaction.getId().toString())
                        .build();
            HttpEntity<String> requestEntity = getRequestEntity(phonePePaymentRequest);
            URI redirectUri = getRedirectionUri(requestEntity);
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
            httpHeaders.add(HttpHeaders.LOCATION, redirectUri.toString());
            httpHeaders.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            httpHeaders.add(HttpHeaders.PRAGMA, "no-cache");
            httpHeaders.add(HttpHeaders.EXPIRES, String.valueOf(0));

            putValueInSession(SessionKeys.WYNK_TRANSACTION_ID, transaction.getId());
            putValueInSession(SessionKeys.SELECTED_PLAN_ID, selectedPlan.getId());
            putValueInSession(SessionKeys.PAYMENT_CODE, PaymentCode.PHONEPE_WALLET);

            publishSQSMessage(reconciliationQueue, reconciliationMessageDelay,
                    PaymentReconciliationMessage.builder()
                            .uid(uid)
                            .planId(planId)
                            .paymentCode(PaymentCode.PHONEPE_WALLET)
                            .transactionId(transaction.getId().toString())
                            .transactionEvent(TransactionEvent.PURCHASE)
                            .initTimestamp(System.currentTimeMillis())
                            .packPeriod(selectedPlan.getPeriod())
                            .build());
            return BaseResponse.<String>builder()
                    .body(redirectUri.toString())
                    .status(HttpStatus.FOUND)
                    .headers(httpHeaders).build();

        }
        catch(Exception e){
            throw new WynkRuntimeException(e);
        }

    }

    @Override
    public <T> BaseResponse<T> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
        return null;
    }


    @Override
    public BaseResponse<ChargingStatus> status(ChargingStatusRequest chargingStatusRequest) {
        ChargingStatus chargingStatus;
        switch (chargingStatusRequest.getFetchStrategy()) {
            case DIRECT_SOURCE_EXTERNAL_WITHOUT_CACHE:
                chargingStatus = fetchChargingStatusFromPhonePeSource(chargingStatusRequest);
                break;
            case DIRECT_SOURCE_INTERNAL_WITHOUT_CACHE:
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

    private void fetchAndUpdateTransactionFromSource(Transaction transaction){

        TransactionStatus finalTransactionStatus;
        PhonePeTransactionResponse phonePeTransactionStatusResponse = getTransactionStatus(transaction.getId().toString());
        if (phonePeTransactionStatusResponse.getSuccess()){
            PhonePeTransactionStatus statusCode = phonePeTransactionStatusResponse.getCode();
            if (statusCode == PhonePeTransactionStatus.PAYMENT_SUCCESS) {
                transaction.setExitTime(Calendar.getInstance());
                finalTransactionStatus = TransactionStatus.SUCCESS;
//                    if (transactionDetails.getPayUUdf1().equalsIgnoreCase(PAYU_SI_KEY)) {
//                        Calendar nextRecurringDateTime = Calendar.getInstance();
//                        nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, chargingStatusRequest.getPackPeriod().getValidity());
//                        recurringPaymentManagerService.addRecurringPayment(transaction.getId().toString(), nextRecurringDateTime);
//                    }
            } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3 &&
                    statusCode == PhonePeTransactionStatus.PAYMENT_PENDING) {
                finalTransactionStatus = TransactionStatus.INPROGRESS;
            } else{
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } else {
            transaction.setExitTime(Calendar.getInstance());
            finalTransactionStatus = TransactionStatus.FAILURE;
        }

//            transaction.setMerchantTransaction(MerchantTransaction.builder()
//                    .externalTransactionId(phonePeTransactionStatusResponse.getData().providerReferenceId)
//                    .request(payUChargingVerificationRequest)
//                    .response(payUChargingVerificationResponse)
//                    .build());
        if (finalTransactionStatus == TransactionStatus.FAILURE) {
            transaction.setPaymentError(PaymentError.builder()
                    .code(phonePeTransactionStatusResponse.getCode().name())
                    .description(phonePeTransactionStatusResponse.getMessage())
                    .build());
        }
        transaction.setStatus(finalTransactionStatus.name());
    }

    private ChargingStatus fetchChargingStatusFromPhonePeSource(ChargingStatusRequest chargingStatusRequest) {
        TransactionStatus existingTransactionStatus;
        TransactionStatus finalTransactionStatus;

        Transaction transaction = transactionManager.get(chargingStatusRequest.getTransactionId()).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY010));

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
        transactionManager.upsert(transaction);

        if (finalTransactionStatus == TransactionStatus.INPROGRESS) {
            logger.error(PaymentLoggingMarker.PHONEPE_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at phonePe end for transactionId {}", chargingStatusRequest.getTransactionId());
            throw new WynkRuntimeException(PaymentErrorType.PAY004);
        } else if (finalTransactionStatus == TransactionStatus.UNKNOWN) {
            logger.error(PaymentLoggingMarker.PHONEPE_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status from phonePe end for transactionId {}", chargingStatusRequest.getTransactionId());
            throw new WynkRuntimeException(PaymentErrorType.PAY003);
        }

        return ChargingStatus.builder()
                .transactionStatus(finalTransactionStatus)
                .build();
    }

    private ChargingStatus fetchChargingStatusFromDataSource(ChargingStatusRequest chargingStatusRequest) {
        String transactionId = getValueFromSession(SessionKeys.WYNK_TRANSACTION_ID);
        Transaction transaction = transactionManager.get(transactionId).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY010));
        return ChargingStatus.builder()
                .transactionStatus(transaction.getStatus())
                .build();
    }

    private URI processCallback(CallbackRequest callbackRequest) {
        final String transactionId = getValueFromSession(SessionKeys.WYNK_TRANSACTION_ID).toString();
        final Transaction transaction = transactionManager.get(transactionId).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY010));

        try {
            Map<String , String> requestPayload = (Map<String, String>) callbackRequest.getBody();
            String uid = getValueFromSession(SessionKeys.UID);
            PlanDTO selectedPlan = getSelectedPlan(getValueFromSession(SessionKeys.SELECTED_PLAN_ID));

            PhonePeTransactionResponse phonePeTransactionResponse = new PhonePeTransactionResponse(requestPayload);

            String errorCode = phonePeTransactionResponse.getCode().name();
            String errorMessage = phonePeTransactionResponse.getMessage();

            Boolean validChecksum = validateChecksum(requestPayload);
            if (validChecksum && phonePeTransactionResponse.getCode() != null) {
                fetchAndUpdateTransactionFromSource(transaction);

                if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                    transaction.setExitTime(Calendar.getInstance());
                    subscriptionServiceManager.publish(selectedPlan.getId(), uid, transactionId, transaction.getStatus(), transaction.getType());
                    if (selectedPlan.getPlanType() == PlanType.SUBSCRIPTION) {
                        Calendar nextRecurringDateTime = Calendar.getInstance();
                        nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, selectedPlan.getPeriod().getValidity());
                        recurringPaymentManagerService.addRecurringPayment(transactionId, nextRecurringDateTime);
                    }
                }
            }
            else {
                logger.error(PaymentLoggingMarker.PHONEPE_CHARGING_CALLBACK_FAILURE,
                        "Invalid checksum found with Wynk transactionId: {}, PhonePe transactionId: {}, Reason: error code: {}, error message: {} for uid: {}",
                        transactionId,
                        phonePeTransactionResponse.getData().getProviderReferenceId(),
                        errorCode,
                        errorMessage,
                        uid);
            }

            URIBuilder returnUrl = new URIBuilder(phonePeCallBackURL);
            returnUrl.addParameter(TRANSACTION_ID, transactionId);
            returnUrl.addParameter(SESSION_ID, SessionContextHolder.get().getId().toString());
            return returnUrl.build();
        }
        catch(Throwable th) {
            throw new WynkRuntimeException(th);
        }
        finally {
            transactionManager.upsert(transaction);
        }
    }

    private HttpEntity<String> getRequestEntity(PhonePePaymentRequest phonePePaymentRequest){
        try {
            String requestJson = Utils.getGson().toJson(phonePePaymentRequest);
            JsonObject jsonRequest = new JsonObject();
            if (StringUtils.isNotEmpty(requestJson)) {
                jsonRequest.addProperty(REQUEST, Utils.encodeBase64(requestJson));
            }
            logger.info(requestJson);
            String xVerifyHeader = Utils.encodeBase64(requestJson) + debitCall + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + "###1";
            MultiValueMap<String, String> headers = new LinkedMultiValueMap<String, String>();
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, "application/json");
            headers.add(X_REDIRECT_URL, phonePeCallBackURL + "?tid=" + phonePePaymentRequest.getTransactionId());
            headers.add(X_REDIRECT_MODE, "POST");
            return new HttpEntity<>(jsonRequest.toString(), headers);
        }
        catch(Exception e){
            throw new WynkRuntimeException(e);
        }
    }

    private URI getRedirectionUri(HttpEntity<String> request){
        try {
            logger.info(phonePeBaseUrl+debitCall);
            logger.info(request.toString());
            URI uri = restTemplate.postForLocation(phonePeBaseUrl + debitCall, request);
//            logger.info(uri.toString());
//            if (uri != null) {
//                insertIntoPollingQueue(trLog);
//            }
            return new URI(phonePeBaseUrl + uri);
        } catch(Exception e) {
            logger.error("Error requesting URL from phonepe",e);
            throw new WynkRuntimeException(e);
        }
    }

    private PhonePeTransactionResponse getTransactionStatus(String transactionId){
        try {
            String prefixStatusApi = "/v3/transaction/" + merchantId + "/";
            String suffixStatusApi = "/status";
            String apiPath = prefixStatusApi + transactionId + suffixStatusApi;
            String xVerifyHeader = apiPath + salt;
            xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + "###1";
            MultiValueMap<String, String> headers = new LinkedMultiValueMap<String, String>();
            headers.add(X_VERIFY, xVerifyHeader);
            headers.add(CONTENT_TYPE, "application/json");
            HttpEntity entity = new HttpEntity(headers);
            ResponseEntity<PhonePeTransactionResponse> responseEntity = restTemplate.exchange(phonePeBaseUrl + apiPath, HttpMethod.GET, entity, PhonePeTransactionResponse.class, new HashMap<>());
            PhonePeTransactionResponse phonePeTransactionResponse = responseEntity.getBody();
            if (phonePeTransactionResponse != null && phonePeTransactionResponse.getCode() != null) {
                logger.info("PhonePe txn response for transaction Id {} :: {}", transactionId, phonePeTransactionResponse);
            }
            return phonePeTransactionResponse;
        }
        catch(Exception e){
            logger.error("Unable to verify status from Phonepe",e);
            throw new WynkRuntimeException(e);
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
                } else if (key.equals("checksum")){
                    checksum = URLDecoder.decode(requestParams.get(key), "UTF-8");
                }
            }
            String calculatedChecksum = DigestUtils.sha256Hex(validationString + salt) + "###1";
                if (StringUtils.equals(checksum, calculatedChecksum)) {
                    validated = true;
                }

        }
        catch (Exception e){
            logger.error("Exception while Checksum validation ", e);
        }
        return validated;
    }

    private PlanDTO getSelectedPlan(int planId) {
        List<PlanDTO> plans = getValueFromSession(SessionKeys.ELIGIBLE_PLANS);
        return plans.stream().filter(plan -> plan.getId() == planId).collect(Collectors.toList()).get(0);
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
    private float getFinalPlanAmountToBePaid(PlanDTO selectedPlan) {
        float finalPlanAmount = selectedPlan.getPrice().getAmount();
        if (selectedPlan.getPrice().getDiscount().size() > 0) {
            for (DiscountDTO discount : selectedPlan.getPrice().getDiscount()) {
                finalPlanAmount *= ((double) (100 - discount.getPercent()) / 100);
            }
        }
        return finalPlanAmount;
    }

    private Transaction initialiseTransaction(ChargingRequest chargingRequest, float amount) {
        return transactionManager.upsert(Transaction.builder()
                .planId(chargingRequest.getPlanId())
                .amount(amount)
                .initTime(Calendar.getInstance())
                .consent(Calendar.getInstance())
                .uid(getValueFromSession(SessionKeys.UID))
                .service(getValueFromSession(SessionKeys.SERVICE))
                .msisdn(getValueFromSession(SessionKeys.MSISDN))
                .paymentChannel(PaymentCode.PHONEPE_WALLET.name())
                .status(TransactionStatus.INPROGRESS.name())
                .type(TransactionEvent.PURCHASE.name())
                .build());
    }
}
