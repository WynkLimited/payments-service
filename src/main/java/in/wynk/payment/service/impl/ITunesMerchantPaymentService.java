package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.DiscountDTO;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.dto.SubscriptionNotificationMessage;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.ItunesIdUidMapping;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.receipts.ItunesIdUidDao;
import in.wynk.payment.dto.ItunesCallbackRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.core.dto.itunes.*;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatus;
import in.wynk.payment.service.IMerchantPaymentCallbackService;
import in.wynk.payment.service.IMerchantIapPaymentVerificationService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.apache.commons.codec.binary.Base64;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.payment.core.dto.itunes.ItunesConstant.*;

@Service(BeanConstant.ITUNES_MERCHANT_PAYMENT_SERVICE)
public class ITunesMerchantPaymentService implements IMerchantIapPaymentVerificationService, IMerchantPaymentCallbackService {


    private final RestTemplate restTemplate;
    private final ITransactionManagerService transactionManager;
    private final ISQSMessagePublisher sqsMessagePublisher;
    private final RateLimiter rateLimiter = RateLimiter.create(6.0);


    @Autowired
    private ItunesIdUidDao itunesIdUidDao;

    @Value("${payment.merchant.itunes.secret}")
    private String itunesSecret;
    @Value("${payment.merchant.itunes.api.url}")
    private String itunesApiUrl;
    @Value("${payment.merchant.itunes.return.wynkurl}")
    private String wynkReturnUrl;
    @Value("${payment.pooling.queue.reconciliation.name}")
    private String reconciliationQueue;
    @Value("${payment.pooling.queue.subscription.name}")
    private String subscriptionQueue;
    @Value("${payment.pooling.queue.reconciliation.sqs.producer.delayInSecond}")
    private int reconciliationMessageDelay;
    @Value("${payment.pooling.queue.subscription.sqs.producer.delayInSecond}")
    private int subscriptionMessageDelay;

    private Logger logger = LoggerFactory.getLogger(ITunesMerchantPaymentService.class.getCanonicalName());

    private static ObjectMapper mapper = new ObjectMapper();
    private static Gson gson = new Gson();

    private static final List<ItunesStatusCodes> failureCodes = Arrays.asList(ItunesStatusCodes.APPLE_21000, ItunesStatusCodes.APPLE_21002, ItunesStatusCodes.APPLE_21003, ItunesStatusCodes.APPLE_21004, ItunesStatusCodes.APPLE_21005,
            ItunesStatusCodes.APPLE_21007, ItunesStatusCodes.APPLE_21008, ItunesStatusCodes.APPLE_21009, ItunesStatusCodes.APPLE_21010);

    public ITunesMerchantPaymentService(RestTemplate restTemplate, ITransactionManagerService transactionManager, ISQSMessagePublisher sqsMessagePublisher) {
        this.restTemplate = restTemplate;
        this.transactionManager = transactionManager;
        this.sqsMessagePublisher = sqsMessagePublisher;
    }


    @Override
    public BaseResponse<String> verifyIap(IapVerificationRequest iapVerificationRequest) {
        try {
            ChargingStatus validationResponse = validateItunesTransaction(iapVerificationRequest.getUid(), iapVerificationRequest.getReceipt(), iapVerificationRequest.getPlanId());
            URIBuilder returnUrl = new URIBuilder(wynkReturnUrl);
            returnUrl.addParameter(STATUS, validationResponse.getTransactionStatus().name());
            HttpHeaders httpHeaders = new HttpHeaders();
            httpHeaders.add(HttpHeaders.CONTENT_TYPE, "text/plain; charset=UTF-8");
            httpHeaders.add(HttpHeaders.LOCATION, returnUrl.toString());
            httpHeaders.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
            httpHeaders.add(HttpHeaders.PRAGMA, "no-cache");
            httpHeaders.add(HttpHeaders.EXPIRES, String.valueOf(0));
            return BaseResponse.<String>builder().body(returnUrl.toString()).status(HttpStatus.FOUND).headers(httpHeaders).build();
        }
        catch (Exception e){
            throw new WynkRuntimeException(e);
        }
    }

    @Override
    public BaseResponse<ChargingStatus> handleCallback(CallbackRequest callbackRequest) {
        try {
            ItunesCallbackRequest itunesCallbackRequest = mapper.readValue(gson.toJson(callbackRequest.getBody()), ItunesCallbackRequest.class);
            ChargingStatus validationResponse = null;
            if (itunesCallbackRequest.getLatestReceiptInfo() != null) {
                LatestReceiptInfo latestReceiptInfo = itunesCallbackRequest.getLatestReceiptInfo();
                String itunesId = latestReceiptInfo.getOriginalTransactionId();
                ItunesIdUidMapping itunesIdUidMapping = itunesIdUidDao.findByItunesId(itunesId);
                String uid = itunesIdUidMapping.getUid();
                int planId = itunesIdUidMapping.getPlanId();
                String decodedReceipt = StringUtils.EMPTY;
                try {
                    decodedReceipt = getModifiedReceipt(itunesCallbackRequest.getLatestReceipt());
                    validationResponse = validateItunesTransaction(uid, decodedReceipt, planId);
                } catch (UnsupportedEncodingException e) {
                    logger.error(BaseLoggingMarkers.PAYMENT_ERROR, String.valueOf(e));
                }
            }
            return BaseResponse.<ChargingStatus>builder().body(validationResponse).status(HttpStatus.OK).build();
        }
        catch (Exception e){
            throw new WynkRuntimeException(e);
        }
    }

    private ChargingStatus validateItunesTransaction(String uid, String requestReceipt, int planId){
        String errorMessage = StringUtils.EMPTY;
        try {
            final PlanDTO selectedPlan = getSelectedPlan(planId);
            final float finalPlanAmount = getFinalPlanAmountToBePaid(selectedPlan);
            Transaction transaction = initialiseTransaction(planId, finalPlanAmount, uid);
            ItunesReceiptType receiptType = ItunesReceiptType.getReceiptType(requestReceipt);
            List<LatestReceiptInfo> userLatestReceipts = getReceiptObjForUser(requestReceipt, receiptType, transaction);
            LatestReceiptInfo latestReceiptInfo = userLatestReceipts.get(0);
            logger.info("latest receipt object: {}", latestReceiptInfo.toString());
            long expireTimestamp = receiptType.getExpireDate(latestReceiptInfo);
            if (expireTimestamp == 0 || expireTimestamp < System.currentTimeMillis()) {
                errorMessage = "Empty receipt info from itunes or expired receipt";
                logger.error(BaseLoggingMarkers.PAYMENT_ERROR, "validateItunesTransaction :: empty or old receipt for uid : {} obj :{} ", uid, latestReceiptInfo);
                transaction.setStatus(TransactionStatus.FAILURE.name());
            }
            else {

                String originalITunesTrxnId = latestReceiptInfo.getOriginalTransactionId();
                String itunesTrxnId = latestReceiptInfo.getTransactionId();
                ItunesIdUidMapping mapping = itunesIdUidDao.findByPlanIdAndItunesId(planId, originalITunesTrxnId);

                // TODO - Is this needed ??
                if (mapping != null && !mapping.getUid().equals(uid)) {
                    logger.error(BaseLoggingMarkers.PAYMENT_ERROR, "Already have subscription for the correcponding itunes id on another account");
                    errorMessage = "Already have subscription for the correcponding itunes id on another account";
                    transaction.setStatus(TransactionStatus.FAILUREALREADYSUBSCRIBED.name());
                }
                logger.info("ItunesIdUidMapping found for ItnuesId :{} , planId: {} = {}", originalITunesTrxnId, planId, mapping);
                if (!StringUtils.isBlank(originalITunesTrxnId) && !StringUtils.isBlank(itunesTrxnId)) {
                    if (mapping == null) {
                        ItunesIdUidMapping itunesIdUidMapping = ItunesIdUidMapping.builder().uid(uid).itunesId(originalITunesTrxnId).planId(planId).receipt(requestReceipt).type(receiptType).build();
                        saveItunesIdUidMapping(itunesIdUidMapping);
                    }
                    publishSQSMessage(subscriptionQueue,
                            subscriptionMessageDelay,
                            SubscriptionNotificationMessage.builder()
                                    .planId(selectedPlan.getId())
                                    .transactionId(transaction.getId().toString())
                                    .transactionEvent(transaction.getType())
                                    .transactionStatus(transaction.getStatus())
                                    .build());
                    transaction.setStatus(TransactionStatus.SUCCESS.name());
                } else {
                    errorMessage = "Itunes transaction Id not found";
                    transaction.setStatus(TransactionStatus.FAILURE.name());
                }
            }

            transaction.setPaymentError(PaymentError.builder()
                    .description(errorMessage)
                    .build());
            transactionManager.upsert(transaction);
            return ChargingStatus
                    .builder()
                    .transactionStatus(transaction.getStatus())
                    .build();

        }
        catch (Exception e) {
            logger.error(BaseLoggingMarkers.PAYMENT_ERROR, "validateItunesTransaction :: raised exception for uid : {} receipt : {} ", uid, requestReceipt, e);
            throw new WynkRuntimeException("Could not process itunes validate transaction request for uid: " + uid + " ERROR: " + e);
        }
    }

    private void createErrorTransaction(Transaction transaction, String errorMsg){
        transaction.setStatus(TransactionStatus.FAILURE.name());
        transaction.setPaymentError(PaymentError.builder()
                .description(errorMsg)
                .build());
        transactionManager.upsert(transaction);
    }

    private Transaction initialiseTransaction(int planId, float amount, String uid) {
        return transactionManager.upsert(Transaction.builder()
                .planId(planId)
                .amount(amount)
                .initTime(Calendar.getInstance())
                .consent(Calendar.getInstance())
                .uid(uid)
                //.service(getValueFromSession(SessionKeys.SERVICE))
                //.msisdn(getValueFromSession(SessionKeys.MSISDN))
                .paymentChannel(PaymentCode.ITUNES.name())
                .status(TransactionStatus.INPROGRESS.name())
                .type(TransactionEvent.PURCHASE.name())
                .build());
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

    private float getFinalPlanAmountToBePaid(PlanDTO selectedPlan) {
        float finalPlanAmount = selectedPlan.getPrice().getAmount();
        if (selectedPlan.getPrice().getDiscount().size() > 0) {
            for (DiscountDTO discount : selectedPlan.getPrice().getDiscount()) {
                finalPlanAmount *= ((double) (100 - discount.getPercent()) / 100);
            }
        }
        return finalPlanAmount;
    }

    private ResponseEntity<String> getItunesStatus(String encodedValue, String password, String url){
        JSONObject requestJson = new JSONObject();
        requestJson.put(RECEIPT_DATA, encodedValue);
        requestJson.put(PASSWORD, password);
        ResponseEntity<String> responseEntity = null;
        try {
            RequestEntity<String> requestEntity = new RequestEntity<>(requestJson.toJSONString(), HttpMethod.POST, URI.create(url));
            responseEntity = restTemplate.exchange(requestEntity, String.class);
        }
        catch (Exception e){
            logger.info("Exception while posting data to itunes for receipt " + requestJson.toString());
            throw e;
        }
        return responseEntity;
    }


    private List<LatestReceiptInfo> getReceiptObjForUser(String receipt, ItunesReceiptType itunesReceiptType, Transaction transaction) {
        try {
            String errorMessage;
            String encodedValue = itunesReceiptType.getEncodedItunesData(receipt);
            if (StringUtils.isBlank(encodedValue)) {
                errorMessage = "Encoded itunes receipt data is empty!";
                createErrorTransaction(transaction, errorMessage);
                logger.error(BaseLoggingMarkers.PAYMENT_ERROR, "Encoded itunes receipt data is empty! for iTunesData {}", receipt);
                throw new WynkRuntimeException(PaymentErrorType.PAY001, errorMessage);
            }
            ResponseEntity<String> appStoreResponse = getItunesStatus(encodedValue, itunesSecret, itunesApiUrl);
            String appStoreResponseBody = appStoreResponse.getBody();
            ItunesReceipt receiptObj = new ItunesReceipt();
            if (itunesReceiptType.equals(ItunesReceiptType.SEVEN)) {
                receiptObj = mapper.readValue(appStoreResponseBody, ItunesReceipt.class);
            }
            else {
                // Handling for type six receipts
                JSONObject receiptFullJsonObj = (JSONObject) JSONValue.parseWithException(appStoreResponseBody);
                LatestReceiptInfo latestReceiptInfo = mapper.readValue(receiptFullJsonObj.get(LATEST_RECEIPT_INFO).toString(), LatestReceiptInfo.class);
                receiptObj.setStatus(receiptFullJsonObj.get(STATUS).toString());
                List<LatestReceiptInfo> latestReceiptInfoList = new ArrayList<>();
                latestReceiptInfoList.add(latestReceiptInfo);
                receiptObj.setLatestReceiptInfoList(latestReceiptInfoList);
            }
            if (receiptObj == null || receiptObj.getStatus() == null) {
                logger.error("Receipt Object returned for response " + appStoreResponseBody + " is not complete!");
                errorMessage = "Receipt Object returned from itunes is null";
                createErrorTransaction(transaction, errorMessage);
                throw new WynkRuntimeException(PaymentErrorType.PAY001, errorMessage);
            }
            int status = Integer.parseInt(receiptObj.getStatus());
            ItunesStatusCodes code = ItunesStatusCodes.getItunesStatusCodes(status);
            if (status == 0) {
                return itunesReceiptType.getSubscriptionDetailJson(receiptObj);
            }
            else {
                if (code != null && failureCodes.contains(code)) {
                    errorMessage = code.getErrorTitle();
                }
                else {
                    code = ItunesStatusCodes.APPLE_21009;
                    errorMessage = "Internal Data Access Error. Try Again Later";
                }
                logger.error(BaseLoggingMarkers.PAYMENT_ERROR, "Failed to subscribe to itunes: response {} request!! status : {} error {}", appStoreResponse, status, errorMessage);
                transaction.setPaymentError(PaymentError.builder()
                        .code(code.toString())
                        .description(code.getErrorTitle())
                        .build());
                transaction.setStatus(TransactionStatus.FAILURE.name());
                transactionManager.upsert(transaction);
                throw new WynkRuntimeException(PaymentErrorType.PAY001, errorMessage);
            }
        }
        catch(Exception e){
            createErrorTransaction(transaction, e.getMessage());
            throw new WynkRuntimeException(e);
        }
    }

    private void saveItunesIdUidMapping(ItunesIdUidMapping itunesIdUidMapping) {
        itunesIdUidDao.save(itunesIdUidMapping);
    }

    private String getModifiedReceipt(String receipt) throws UnsupportedEncodingException {
        String decodedReceipt;
        decodedReceipt = new String(Base64.decodeBase64(receipt), StandardCharsets.UTF_8);
        decodedReceipt = decodedReceipt.replaceAll(";(?=[^;]*$)", "");
        decodedReceipt = decodedReceipt.replaceAll(";", ",");
        decodedReceipt = decodedReceipt.replaceAll("\" = \"", "\" : \"");
        return decodedReceipt;

    }
}