package in.wynk.payment.service.impl;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.constant.BeanConstant;
import in.wynk.payment.dto.Itunes.ItunesIdUidMapping;
import in.wynk.payment.dto.Itunes.SubscriberStatus;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ItunesResponse;
import in.wynk.payment.enums.ItunesReceiptType;
import in.wynk.payment.enums.ItunesStatusCodes;
import in.wynk.payment.service.IMerchantPaymentStatusService;
import javafx.util.Pair;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoOperations;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service(BeanConstant.ITUNES_MERCHANT_PAYMENT_SERVICE)
public class ITunesMerchantPaymentService implements IMerchantPaymentStatusService {

    @Autowired
    private RestTemplate iTunesRestTemplate;

    @Autowired
    private MongoOperations iTunesMongoTemplate;

    private Logger logger = LoggerFactory.getLogger(ITunesMerchantPaymentService.class.getCanonicalName());

    private static final List<ItunesStatusCodes> failureCodes = Arrays.asList(ItunesStatusCodes.APPLE_21000, ItunesStatusCodes.APPLE_21002, ItunesStatusCodes.APPLE_21003, ItunesStatusCodes.APPLE_21004, ItunesStatusCodes.APPLE_21005,
            ItunesStatusCodes.APPLE_21007, ItunesStatusCodes.APPLE_21008);

    @Override
    public <T> BaseResponse<T> status(ChargingStatusRequest chargingStatusRequest) {
        String requestReceipt = chargingStatusRequest.getReceipt();
        String uid = chargingStatusRequest.getUid();
        String tid = chargingStatusRequest.getTransactionId();
        //ResponseEntity<String> responseEntity  = getItunesStatus(requestReceipt, itunesSecret, url); // Get response from APP Store
        List<ItunesResponse> validationResponse = validateUserSubscription(uid, requestReceipt, tid);
        return (BaseResponse<T>) BaseResponse.builder().body(validationResponse).status(HttpStatus.OK).build();

    }

    private List<ItunesResponse> validateUserSubscription(String uid, String requestReceipt, String transactionId){
        ItunesReceiptType receiptType = ItunesReceiptType.getReceiptType(requestReceipt);
        JSONArray userLatestReceipts = getReceiptObjForUser(requestReceipt, receiptType, uid);
        List<JSONObject> latestSortedReceipts = getLatestReceipts(userLatestReceipts, receiptType);
        String errorMessge = StringUtils.EMPTY;
        List<ItunesResponse> itunesResponseList = null;
        Map<String, Pair<Integer, SubscriberStatus>> subsMapRaw = new HashMap<>();
        for(JSONObject receiptObject : latestSortedReceipts){
            try{
                long expireTimestamp = receiptType.getExpireDate(receiptObject);
                if(receiptObject == null || expireTimestamp == 0) {
                    logger.error(BaseLoggingMarkers.APPLICATION_ERROR, "updateITuneSubscription :: empty receipt or expires_date for uid : {} obj :{} ", uid, receiptObject);
                    errorMessge = "Empty receipt or expires_date";
                    continue;
                }
                if(expireTimestamp < System.currentTimeMillis()) {
                    logger.error(BaseLoggingMarkers.APPLICATION_ERROR, "updateITuneSubscription :: old receipt with expired validity found for uid : {} obj :{} ", uid, receiptObject);
                    errorMessge = "Old receipt with expired validity found";
                    break;  // Because List is reverse sorted on expiry timestamp
                }
                String productId = (String) receiptObject.get("product_id");

                // TODO - Get and Validate pack using iTunes Product Id
//                if(pack == null || pack.getService() != service) {
//                    logger.error(LoggingMarkers.APPLICATION_ERROR, "no pack with external productid:{}  uid : {} obj :{} ", productId, uid, receiptJsonObject);
//                    errorMessge = "No pack with particular productid";
//                    continue;
//                }

                String originalITunesTrxnId = (String) receiptObject.get("original_transaction_id");
                String itunesTrxnId = (String) receiptObject.get("transaction_id");

                ItunesIdUidMapping mapping = getItunesIdUidMappingFromTrxnId(originalITunesTrxnId,12001); //  pack.getPartnerProductId() - hardcoded for now
                if(mapping != null && !mapping.getKey().getUid().equals(uid)) {
                    logger.error(BaseLoggingMarkers.APPLICATION_INVALID_USECASE, "Already have subscription for the correcponding itunes id on another account");
                    errorMessge = "Already have subscription for the correcponding itunes id on another account";
                    continue;
                }
                // TODO - Check Subscription Eligibility
//                Pair<Boolean, SubscriptionStatus> eligiblity = subscriptionService.checkSubscriptionEligiblity(service, uid, pack.getPartnerProductId(), expireTimestamp);
//                if(!eligiblity.getKey()) {
//                    Subscription currentSub = subscriptionService.getSubscriptionForPartnerProductId(service, uid, pack.getPartnerProductId());
//                    if(currentSub == null || !currentSub.getPaymentMethod().equals(PaymentMethod.ITUNES)) {
//                        sendSMStoDeactivateSubscription(pack, uid);
//                        logger.error(LoggingMarkers.APPLICATION_INVALID_USECASE, "Already have subscription!");
//                        errorMessge = "Already have subscription!";
//                        continue;
//                    }
//
//                }


                if(!StringUtils.isBlank(originalITunesTrxnId) && !StringUtils.isBlank(itunesTrxnId)) {
                    //saveItunesIdUidMapping(originalITunesTrxnId, pack.getPartnerProductId(), uid, requestReceipt, receiptType);
                    long lastSubscribedTimestamp = receiptType.getPurchaseDate(receiptObject);
                    //String tid = createTransactionLog(pack, TransactionEvent.SUBSCRIBE, lastSubscribedTimestamp, uid, service, receipt, itunesTrxnId, isFreeTrial, clientTid);
                    //subscriptionHandlingService.createSubscription(service, uid, pack.getPartnerProductId(), PaymentMethod.ITUNES, new HashMap<String, String>(), expireTimestamp, false, tid);
                    //SubscriberStatus status = new SubscriberStatus();
                    //status.setStatus(subscriptionService.getSubscriptionStateForPartnerProductId(service, uid, pack.getPartnerProductId()));
                    //status.setExpireTimestamp(expireTimestamp);
                    //subsMap.put(pack.getPartnerProductId(), status);
                    //subsMapRaw.put(pack.getPackageGroup(), new Pair<Integer, SubscriberStatus>(pack.getPartnerProductId(), status));
                    //StringBuilder stringBuilder = new StringBuilder();
                    //itunesResponseList.add(new ItunesResponse(pack.getPartnerProductId, Integer.valueOf(stringBuilder.append(productId).append(PaymentMethod.ITUNES.getId()).toString()), ));

                }
                else {
                    //createErrorTransactionLog(pack, TransactionEvent.SUBSCRIBE, uid, service, receipt, itunesTrxnId, StringUtils.EMPTY, receiptJsonObject.toJSONString(), StringUtils.EMPTY);
                }

            }
            catch(Exception e){

            }
        }
        return itunesResponseList;
    }


    private ResponseEntity<String> getItunesStatus(String encodedValue, String password, String url){
        Map<String, String> requestJson = new HashMap<>();
        requestJson.put("receipt-data", encodedValue);
        requestJson.put("password", password);
        ResponseEntity<String> responseEntity = null;
        try {
            RequestEntity<String> requestEntity = new RequestEntity<>(requestJson.toString(), HttpMethod.POST, URI.create(url));
            responseEntity = iTunesRestTemplate.exchange(requestEntity, String.class);
        }
        catch (Exception e){
            logger.info("Exception while posting data to itunes for receipt " + requestJson.toString());
            throw e;
        }
        return responseEntity;
    }


    private List<JSONObject> getLatestReceipts(JSONArray receiptJsonArr, ItunesReceiptType type) {
        List<JSONObject> unsortedReceipts = (List<JSONObject>) receiptJsonArr;
       return unsortedReceipts.parallelStream()
                .sorted(Comparator.comparingLong(type::getExpireDate).reversed())
                .collect(Collectors.toList());
    }



    private JSONArray getReceiptObjForUser(String receipt, ItunesReceiptType itunesReceiptType, String uid) {
        String itunesSecret = "dggrg"; // set in config
        String itunesApiUrl = "sandbox.com"; // set in config
        String encodedValue = itunesReceiptType.getEncodedItunesData(receipt);
        if(org.apache.commons.lang.StringUtils.isBlank(encodedValue)) {
            logger.error("Encoded itunes / itunes data is empty! for iTunesData {}", receipt);
            //throw new PortalException(WynkErrorType.BSY010, "Encoded itunes / itunes data is empty! for iTunesData");
        }
        ResponseEntity<String> appStoreResponse = getItunesStatus(encodedValue, itunesSecret, itunesApiUrl);
        String appStoreResponseBody = appStoreResponse.getBody();
        JSONObject receiptFullJsonObj = null;
        try {
            receiptFullJsonObj = (JSONObject) JSONValue.parseWithException(appStoreResponseBody);
        }
        catch (ParseException e) {
            logger.error("Error while parsing itunes receipt received {} ", appStoreResponseBody);

        }
        if(receiptFullJsonObj == null || receiptFullJsonObj.get("status") == null) {
            logger.error("Failed to subscribe to itunes:: Receipt returned for response " + appStoreResponseBody + " is not complete!");
//            createErrorTransactionLog(null, TransactionEvent.SUBSCRIBE, uid, service, receipt, org.apache.commons.lang.StringUtils.EMPTY, receiptFullJsonObj.toString(), response, null);
//            throw new PortalException(WynkErrorType.BSY008, "Failed to subscribe to itunes");
        }
        int status = ((Number) receiptFullJsonObj.get("status")).intValue();
        ItunesStatusCodes code = ItunesStatusCodes.getItunesStatusCodes(status);
        if(code != null && failureCodes.contains(code)) {
            String errorMessage = code == null? Integer.toString(status) : code.getErrorTitle();
            logger.error(" Failed to subscribe to itunes: response {} request!! status : {} error {}", appStoreResponse, status, errorMessage);
            //createErrorTransactionLog(null, TransactionEvent.SUBSCRIBE, uid, service, receipt, StringUtils.EMPTY, errorMessage, response, Integer.toString(status));
            //throw new PortalException(WynkErrorType.BSY008, errorMessage);
        }
        return itunesReceiptType.getSubscriptionDetailJson(receiptFullJsonObj);
    }




    private ItunesIdUidMapping getItunesIdUidMappingFromTrxnId(String trxnId, int productid) {
        Query query = new Query(Criteria.where("_id.productId").is(productid).and("itunesId").is(trxnId));
        return iTunesMongoTemplate.findOne(query, ItunesIdUidMapping.class);
    }
    private void saveItunesIdUidMapping(String itunesId, int productid, String uid, String secret, ItunesReceiptType type) {
        ItunesIdUidMapping mapping = new ItunesIdUidMapping();
        mapping.setKey(new ItunesIdUidMapping.Key(uid, productid));
        mapping.setSecret(secret);
        mapping.setItunesId(itunesId);
        mapping.setType(type);
        iTunesMongoTemplate.save(mapping);
    }

}