package in.wynk.payment.service.impl;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.constant.BeanConstant;
import in.wynk.payment.constant.ItunesConstant;
import in.wynk.payment.constant.PaymentErrorType;
import in.wynk.payment.dao.entity.ItunesIdUidMapping;
import in.wynk.payment.dao.receipts.ItunesIdUidDao;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ItunesResponse;
import in.wynk.payment.enums.ItunesReceiptType;
import in.wynk.payment.enums.ItunesStatusCodes;
import in.wynk.payment.service.IMerchantPaymentStatusService;
import org.apache.commons.lang.StringUtils;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.json.simple.parser.ParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.net.URI;
import java.util.*;

@Service(BeanConstant.ITUNES_MERCHANT_PAYMENT_SERVICE)
public class ITunesMerchantPaymentService implements IMerchantPaymentStatusService {

    @Autowired
    private RestTemplate iTunesRestTemplate;

    @Autowired
    private ItunesIdUidDao itunesIdUidDao;

    private Logger logger = LoggerFactory.getLogger(ITunesMerchantPaymentService.class.getCanonicalName());

    private static final List<ItunesStatusCodes> failureCodes = Arrays.asList(ItunesStatusCodes.APPLE_21000, ItunesStatusCodes.APPLE_21002, ItunesStatusCodes.APPLE_21003, ItunesStatusCodes.APPLE_21004, ItunesStatusCodes.APPLE_21005,
            ItunesStatusCodes.APPLE_21007, ItunesStatusCodes.APPLE_21008, ItunesStatusCodes.APPLE_21009, ItunesStatusCodes.APPLE_21010);

    @Override
    public <T> BaseResponse<T> status(ChargingStatusRequest chargingStatusRequest) {
        String requestReceipt = chargingStatusRequest.getReceipt();
        String uid = chargingStatusRequest.getUid();
        String tid = chargingStatusRequest.getTransactionId();
        ItunesResponse validationResponse = validateItunesTransaction(uid, requestReceipt, tid);
        return (BaseResponse<T>) BaseResponse.builder().body(validationResponse).status(HttpStatus.OK).build();
    }

    // TODO - Add Info Logs and create Wynk Error Codes
    private ItunesResponse validateItunesTransaction(String uid, String requestReceipt, String transactionId){
        String errorMessge = StringUtils.EMPTY;
        ItunesResponse itunesResponse = new ItunesResponse();
        try {
            ItunesReceiptType receiptType = ItunesReceiptType.getReceiptType(requestReceipt);
            JSONArray userLatestReceipts = getReceiptObjForUser(requestReceipt, receiptType, uid);
            JSONObject receiptObject = (JSONObject) userLatestReceipts.get(0);
            logger.info("latest receipt object: {}", receiptObject.toJSONString());
            long expireTimestamp = receiptType.getExpireDate(receiptObject);
            if (receiptObject == null || expireTimestamp == 0) {
                logger.error(BaseLoggingMarkers.APPLICATION_ERROR, "validateItunesTransaction :: empty receipt or expires_date for uid : {} obj :{} ", uid, receiptObject);
                errorMessge = "Empty receipt or expires_date";
                itunesResponse.setErrorMsg(errorMessge);
                return itunesResponse;
            }
            if (expireTimestamp < System.currentTimeMillis()) {
                logger.error(BaseLoggingMarkers.APPLICATION_ERROR, "validateItunesTransaction :: old receipt with expired validity found for uid : {} obj :{} ", uid, receiptObject);
                errorMessge = "Old receipt with expired validity found";
                itunesResponse.setErrorMsg(errorMessge);
                return itunesResponse;
            }

            String productId = (String) receiptObject.get(ItunesConstant.PRODUCT_ID);
            String originalITunesTrxnId = (String) receiptObject.get(ItunesConstant.ORIGINAL_TRANSACTION_ID);
            String itunesTrxnId = (String) receiptObject.get(ItunesConstant.TRANSACTION_ID);

            // TODO - Get partner product ID to fecth and save in mongo ItunesIdUidMapping
            ItunesIdUidMapping mapping = getItunesIdUidMappingFromTrxnId(uid, 12005, originalITunesTrxnId);
            logger.info("ItunesIdUidMapping found for uid :{} , productId: {} = {}", uid, productId, mapping);
            // TODO - Emit Transaction Event And return response Accordingly
            if (!StringUtils.isBlank(originalITunesTrxnId) && !StringUtils.isBlank(itunesTrxnId)) {
                saveItunesIdUidMapping(originalITunesTrxnId, 12005, uid, requestReceipt, receiptType);
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
        catch (Exception e) {
            logger.error(BaseLoggingMarkers.APPLICATION_ERROR, "validateItunesTransaction :: raised exception for uid : {} receipt : {} ", uid, requestReceipt, e);
            throw new RuntimeException("Could not process itunes validate transaction request for uid: " + uid + " ERROR: " + e);
        }
        return itunesResponse;
    }


    private ResponseEntity<String> getItunesStatus(String encodedValue, String password, String url){
        JSONObject requestJson = new JSONObject();
        requestJson.put(ItunesConstant.RECEIPT_DATA, encodedValue);
        requestJson.put(ItunesConstant.PASSWORD, password);
        ResponseEntity<String> responseEntity = null;
        System.out.println(requestJson.toString());
        try {
            RequestEntity<String> requestEntity = new RequestEntity<>(requestJson.toJSONString(), HttpMethod.POST, URI.create(url));
            responseEntity = iTunesRestTemplate.exchange(requestEntity, String.class);
        }
        catch (Exception e){
            logger.info("Exception while posting data to itunes for receipt " + requestJson.toString());
            throw e;
        }
        return responseEntity;
    }

    private JSONArray getReceiptObjForUser(String receipt, ItunesReceiptType itunesReceiptType, String uid) {
        String itunesSecret = "820ea8abe1374b369eaa564dcfa6391c";           // set in config and Autowire here
        String itunesApiUrl = "https://buy.itunes.apple.com/verifyReceipt";     // set in config Autowire here
        String encodedValue = itunesReceiptType.getEncodedItunesData(receipt);
        if (StringUtils.isBlank(encodedValue)) {
            logger.error("Encoded itunes / itunes data is empty! for iTunesData {}", receipt);
            //throw new PortalException(WynkErrorType.BSY010, "Encoded itunes / itunes data is empty! for iTunesData");
        }
        ResponseEntity<String> appStoreResponse = getItunesStatus(encodedValue, itunesSecret, itunesApiUrl);
        if (!appStoreResponse.getStatusCode().is2xxSuccessful()) {
            logger.error("Failed to get receipt object from itunes :: Receipt returned for response is null!");
//            createErrorTransactionLog(null, TransactionEvent.SUBSCRIBE, uid, service, receipt, org.apache.commons.lang.StringUtils.EMPTY, receiptFullJsonObj.toString(), response, null);
//            throw new PortalException(WynkErrorType.BSY008, "Failed to subscribe to itunes");
        }
        String appStoreResponseBody = appStoreResponse.getBody();
        JSONObject receiptJson = null;
        try {
            receiptJson = (JSONObject) JSONValue.parseWithException(appStoreResponseBody);
        } catch (ParseException e) {
            logger.error("Error while parsing, itunes receipt received : {} ", appStoreResponseBody);
        }
        if (receiptJson == null || !receiptJson.containsKey(ItunesConstant.STATUS)) {
            logger.error("Receipt Object returned for response " + appStoreResponseBody + " is not complete!");
//            createErrorTransactionLog(null, TransactionEvent.SUBSCRIBE, uid, service, receipt, org.apache.commons.lang.StringUtils.EMPTY, receiptFullJsonObj.toString(), response, null);
            throw new WynkRuntimeException(PaymentErrorType.PAY001, "Failed to subscribe to itunes");
        }
        int status = ((Number) receiptJson.get(ItunesConstant.STATUS)).intValue();
        ItunesStatusCodes code = ItunesStatusCodes.getItunesStatusCodes(status);
        if (status == 0 || status == 21006) {
            return itunesReceiptType.getSubscriptionDetailJson(receiptJson);
        }
        else {
            String errorMessage;
            if (code != null && failureCodes.contains(code)) {
                errorMessage = code.getErrorTitle();
            } else {
                errorMessage = "Internal Data Access Error. Try Again Later";
            }
            logger.error(" Failed to subscribe to itunes: response {} request!! status : {} error {}", appStoreResponse, status, errorMessage);
            //createErrorTransactionLog(null, TransactionEvent.SUBSCRIBE, uid, service, receipt, StringUtils.EMPTY, errorMessage, response, Integer.toString(status));
            throw new WynkRuntimeException(PaymentErrorType.PAY001, errorMessage);
        }
    }


    private ItunesIdUidMapping getItunesIdUidMappingFromTrxnId(String uid, Integer productid, String originalITunesTrxnId) {
        logger.info("fetching data with uid : {} and productId : {} from ItunesIdUisMapping", uid, productid);
        return itunesIdUidDao.findByKeyAnditunesId(new ItunesIdUidMapping.Key(uid, productid), originalITunesTrxnId);
    }
    private void saveItunesIdUidMapping(String itunesId, int productid, String uid, String receipt, ItunesReceiptType type) {
        ItunesIdUidMapping mapping = new ItunesIdUidMapping(new ItunesIdUidMapping.Key(uid, productid),itunesId, receipt, type);
        itunesIdUidDao.save(mapping);
    }

}