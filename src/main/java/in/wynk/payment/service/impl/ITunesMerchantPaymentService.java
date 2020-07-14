package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.DiscountDTO;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.ItunesIdUidMapping;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.receipts.ItunesIdUidDao;
import in.wynk.payment.core.dto.itunes.ItunesReceipt;
import in.wynk.payment.core.dto.itunes.ItunesReceiptType;
import in.wynk.payment.core.dto.itunes.ItunesStatusCodes;
import in.wynk.payment.core.dto.itunes.LatestReceiptInfo;
import in.wynk.payment.dto.ItunesCallbackRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.IapVerificationRequest;
import in.wynk.payment.dto.request.ItunesVerificationRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatus;
import in.wynk.payment.service.IMerchantIapPaymentVerificationService;
import in.wynk.payment.service.IMerchantPaymentCallbackService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.List;

import static in.wynk.payment.core.dto.itunes.ItunesConstant.LATEST_RECEIPT_INFO;
import static in.wynk.payment.core.dto.itunes.ItunesConstant.PASSWORD;
import static in.wynk.payment.core.dto.itunes.ItunesConstant.RECEIPT_DATA;
import static in.wynk.payment.core.dto.itunes.ItunesConstant.STATUS;

@Service(BeanConstant.ITUNES_PAYMENT_SERVICE)
public class ITunesMerchantPaymentService implements IMerchantIapPaymentVerificationService, IMerchantPaymentCallbackService {


    private final RestTemplate restTemplate;
    private final ITransactionManagerService transactionManager;
    private final ISQSMessagePublisher sqsMessagePublisher;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final PaymentCachingService cachingService;
    private final RateLimiter rateLimiter = RateLimiter.create(6.0);


    @Autowired
    private ItunesIdUidDao itunesIdUidDao;

    @Value("${payment.merchant.itunes.secret}")
    private String itunesSecret;
    @Value("${payment.merchant.itunes.api.url}")
    private String itunesApiUrl;
    @Value("${payment.status.web.url}")
    private String statusWebUrl;
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

    public ITunesMerchantPaymentService(RestTemplate restTemplate, ITransactionManagerService transactionManager, ISQSMessagePublisher sqsMessagePublisher, ISubscriptionServiceManager subscriptionServiceManager, PaymentCachingService cachingService) {
        this.restTemplate = restTemplate;
        this.transactionManager = transactionManager;
        this.sqsMessagePublisher = sqsMessagePublisher;
        this.subscriptionServiceManager = subscriptionServiceManager;
        this.cachingService = cachingService;
    }


    @Override
    public BaseResponse<Void> verifyReceipt(IapVerificationRequest iapVerificationRequest) {
        try {
            ItunesVerificationRequest request = (ItunesVerificationRequest) iapVerificationRequest;
            ChargingStatus validationResponse = validateItunesTransaction(iapVerificationRequest.getUid(), request.getMsisdn(), request.getReceipt(), iapVerificationRequest.getPlanId(), getValueFromSession(SessionKeys.SERVICE));
            URIBuilder returnUrl = new URIBuilder(statusWebUrl);
            returnUrl.addParameter(STATUS, validationResponse.getTransactionStatus().name());
             return BaseResponse.redirectResponse(returnUrl.build().toString());
        }
        catch (Exception e){
            throw new WynkRuntimeException(WynkErrorType.UT999, e.getMessage());
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
                String msisdn = itunesIdUidMapping.getMsisdn();
                String service = itunesIdUidMapping.getService();
                int planId = itunesIdUidMapping.getPlanId();
                String decodedReceipt = StringUtils.EMPTY;
                try {
                    decodedReceipt = getModifiedReceipt(itunesCallbackRequest.getLatestReceipt());
                    validationResponse = validateItunesTransaction(uid,msisdn, decodedReceipt, planId, service);
                } catch (UnsupportedEncodingException e) {
                    logger.error(BaseLoggingMarkers.PAYMENT_ERROR, String.valueOf(e));
                }
            }
            return BaseResponse.<ChargingStatus>builder().body(validationResponse).status(HttpStatus.OK).build();
        }
        catch (Exception e){
            throw new WynkRuntimeException(WynkErrorType.UT999, e.getMessage());
        }
    }

    private ChargingStatus validateItunesTransaction(String uid, String msisdn, String requestReceipt, int planId, String service){
        String errorMessage = StringUtils.EMPTY;
        final PlanDTO selectedPlan = cachingService.getPlan(planId);
        final double finalPlanAmount = selectedPlan.getFinalPrice();
        Transaction transaction = transactionManager.initiateTransaction(uid, msisdn, planId, finalPlanAmount, PaymentCode.ITUNES, service);

        try {
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

                if (mapping != null && !mapping.getUid().equals(uid)) {
                    logger.error(BaseLoggingMarkers.PAYMENT_ERROR, "Already have subscription for the correcponding itunes id on another account");
                    errorMessage = "Already have subscription for the correcponding itunes id on another account";
                    transaction.setStatus(TransactionStatus.FAILUREALREADYSUBSCRIBED.name());
                }
                logger.info("ItunesIdUidMapping found for ItnuesId :{} , planId: {} = {}", originalITunesTrxnId, planId, mapping);
                if (!StringUtils.isBlank(originalITunesTrxnId) && !StringUtils.isBlank(itunesTrxnId)) {
                    if (mapping == null) {
                        ItunesIdUidMapping itunesIdUidMapping = ItunesIdUidMapping.builder().msisdn(msisdn).uid(uid).itunesId(originalITunesTrxnId).planId(planId)
                                        .receipt(requestReceipt).type(receiptType).service(service).build();
                        saveItunesIdUidMapping(itunesIdUidMapping);
                    }
                    transaction.setStatus(TransactionStatus.SUCCESS.name());
                } else {
                    errorMessage = "Itunes transaction Id not found";
                    transaction.setStatus(TransactionStatus.FAILURE.name());
                }
            }

            if(transaction.getStatus() == TransactionStatus.SUCCESS){
                transaction.setExitTime(Calendar.getInstance());
                subscriptionServiceManager.publish(selectedPlan.getId(), uid, transaction.getId().toString(), transaction.getStatus(), transaction.getType());

            }
            else {
                transaction.setPaymentError(PaymentError.builder()
                        .description(errorMessage)
                        .build());
            }
            return ChargingStatus
                    .builder()
                    .transactionStatus(transaction.getStatus())
                    .build();

        }
        catch (Exception e) {
            logger.error(BaseLoggingMarkers.PAYMENT_ERROR, "validateItunesTransaction :: raised exception for uid : {} receipt : {} ", uid, requestReceipt, e);
            throw new WynkRuntimeException(WynkErrorType.UT999, "Could not process itunes validate transaction request for uid: " + uid + " ERROR: " + e);
        }
        finally {
            transactionManager.upsert(transaction);
        }
    }

    private void createErrorTransaction(Transaction transaction, String errorMsg){
        transaction.setStatus(TransactionStatus.FAILURE.name());
        transaction.setPaymentError(PaymentError.builder()
                .description(errorMsg)
                .build());
        transactionManager.upsert(transaction);
    }

    private <T> T getValueFromSession(String key) {
        Session<SessionDTO> session = SessionContextHolder.get();
        return session.getBody().get(key);
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
                throw new WynkRuntimeException(PaymentErrorType.PAY011, errorMessage);
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
                throw new WynkRuntimeException(PaymentErrorType.PAY011, errorMessage);
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
                throw new WynkRuntimeException(PaymentErrorType.PAY011, errorMessage);
            }
        }
        catch(Exception e){
            createErrorTransaction(transaction, e.getMessage());
            throw new WynkRuntimeException(WynkErrorType.UT999, e.getMessage());
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