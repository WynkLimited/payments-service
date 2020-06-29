package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.gson.Gson;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dao.entity.ItunesIdUidMapping;
import in.wynk.payment.dao.receipts.ItunesIdUidDao;
import in.wynk.payment.dto.VerificationRequest;
import in.wynk.payment.dto.itunes.*;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ItunesResponse;
import in.wynk.payment.service.IMerchantPaymentCallbackService;
import in.wynk.payment.service.IMerchantPaymentVerificationService;
import org.apache.commons.lang.StringUtils;
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
import org.apache.commons.codec.binary.Base64;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.*;

import static in.wynk.payment.constant.ItunesConstant.*;

@Service(BeanConstant.ITUNES_MERCHANT_PAYMENT_SERVICE)
public class ITunesMerchantPaymentService implements IMerchantPaymentVerificationService, IMerchantPaymentCallbackService {

    @Autowired
    private RestTemplate iTunesRestTemplate;

    @Autowired
    private ItunesIdUidDao itunesIdUidDao;

    @Value("${itunes.secret}")
    private String itunesSecret;

    @Value("${itunes.api.url}")
    private String itunesApiUrl;

    private Logger logger = LoggerFactory.getLogger(ITunesMerchantPaymentService.class.getCanonicalName());

    private static ObjectMapper mapper = new ObjectMapper();
    private static Gson gson = new Gson();

    private static final List<ItunesStatusCodes> failureCodes = Arrays.asList(ItunesStatusCodes.APPLE_21000, ItunesStatusCodes.APPLE_21002, ItunesStatusCodes.APPLE_21003, ItunesStatusCodes.APPLE_21004, ItunesStatusCodes.APPLE_21005,
            ItunesStatusCodes.APPLE_21007, ItunesStatusCodes.APPLE_21008, ItunesStatusCodes.APPLE_21009, ItunesStatusCodes.APPLE_21010);


    @Override
    public BaseResponse<ItunesResponse> doVerify(VerificationRequest verificationRequest) {
        ItunesResponse validationResponse = validateItunesTransaction(verificationRequest.getUid(), verificationRequest.getReceipt(), verificationRequest.getPlanId());
        // TODO - send redirection url based on success/failure
        return BaseResponse.<ItunesResponse>builder().body(validationResponse).status(HttpStatus.OK).build();
    }

    @Override
    public BaseResponse<ItunesResponse> handleCallback(CallbackRequest callbackRequest) {
        try {
            ItunesCallbackRequest itunesCallbackRequest = mapper.readValue(gson.toJson(callbackRequest.getBody()), ItunesCallbackRequest.class);
            ItunesResponse validationResponse = null;
            if (itunesCallbackRequest.getLatestReceiptInfo() != null) {
                LatestReceiptInfo latestReceiptInfo = itunesCallbackRequest.getLatestReceiptInfo();
                String itunesId = latestReceiptInfo.getOriginalTransactionId();
                ItunesIdUidMapping itunesIdUidMapping = itunesIdUidDao.findByitunesId(itunesId);
                String uid = itunesIdUidMapping.getKey().getUid();
                int planId = itunesIdUidMapping.getKey().getProductId();
                String decodedReceipt = StringUtils.EMPTY;
                try {
                    decodedReceipt = getModifiedReceipt(itunesCallbackRequest.getLatestReceipt());
                    validationResponse = validateItunesTransaction(uid, decodedReceipt, planId);
                } catch (UnsupportedEncodingException e) {
                    logger.error(String.valueOf(e));
                }
            }
            return BaseResponse.<ItunesResponse>builder().body(validationResponse).status(HttpStatus.OK).build();
        }  catch (Exception e){
            throw new WynkRuntimeException(e);
        }
    }


    // TODO - Add Info Logs and create Wynk Error Codes
    private ItunesResponse validateItunesTransaction(String uid, String requestReceipt, int planId){
        String errorMessge;
        ItunesResponse itunesResponse = new ItunesResponse();
        try {
            ItunesReceiptType receiptType = ItunesReceiptType.getReceiptType(requestReceipt);
            List<LatestReceiptInfo> userLatestReceipts = getReceiptObjForUser(requestReceipt, receiptType, uid);
            LatestReceiptInfo latestReceiptInfo = userLatestReceipts.get(0);
            logger.info("latest receipt object: {}", latestReceiptInfo.toString());
            long expireTimestamp = receiptType.getExpireDate(latestReceiptInfo);
            if (expireTimestamp == 0) {
                logger.error(BaseLoggingMarkers.APPLICATION_ERROR, "validateItunesTransaction :: empty receipt or expires_date for uid : {} obj :{} ", uid, latestReceiptInfo);
                errorMessge = "Empty receipt or expires_date";
                itunesResponse.setErrorMsg(errorMessge);
                return itunesResponse;
            }
            if (expireTimestamp < System.currentTimeMillis()) {
                logger.error(BaseLoggingMarkers.APPLICATION_ERROR, "validateItunesTransaction :: old receipt with expired validity found for uid : {} obj :{} ", uid, latestReceiptInfo);
                errorMessge = "Old receipt with expired validity found";
                itunesResponse.setErrorMsg(errorMessge);
                return itunesResponse;
            }

            String productId = latestReceiptInfo.getProductId();
            String originalITunesTrxnId = latestReceiptInfo.getOriginalTransactionId();
            String itunesTrxnId = latestReceiptInfo.getTransactionId();

            // TODO - Get partner product ID to fecth and save in mongo ItunesIdUidMapping
            ItunesIdUidMapping mapping = getItunesIdUidMappingFromTrxnId(planId, originalITunesTrxnId);
            if(mapping != null && !mapping.getKey().getUid().equals(uid)) {
                logger.error(BaseLoggingMarkers.APPLICATION_INVALID_USECASE, "Already have subscription for the correcponding itunes id on another account");
                errorMessge = "Already have subscription for the correcponding itunes id on another account";
                itunesResponse.setErrorMsg(errorMessge);
                return itunesResponse;
            }
            logger.info("ItunesIdUidMapping found for ItnuesId :{} , productId: {} = {}", originalITunesTrxnId, productId, mapping);
            // TODO - Emit Transaction Event And return response Accordingly
            if (!StringUtils.isBlank(originalITunesTrxnId) && !StringUtils.isBlank(itunesTrxnId)) {
                saveItunesIdUidMapping(originalITunesTrxnId, planId, uid, requestReceipt, receiptType);
                long lastSubscribedTimestamp = receiptType.getPurchaseDate(latestReceiptInfo);
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
        requestJson.put(RECEIPT_DATA, encodedValue);
        requestJson.put(PASSWORD, password);
        ResponseEntity<String> responseEntity = null;
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

    private List<LatestReceiptInfo> getReceiptObjForUser(String receipt, ItunesReceiptType itunesReceiptType, String uid) {
        String encodedValue = itunesReceiptType.getEncodedItunesData(receipt);
        if (StringUtils.isBlank(encodedValue)) {
            logger.error("Encoded itunes / itunes data is empty! for iTunesData {}", receipt);
            //throw new PortalException(WynkErrorType.BSY010, "Encoded itunes / itunes data is empty! for iTunesData");
        }
        ResponseEntity<String> appStoreResponse = getItunesStatus(encodedValue, itunesSecret, itunesApiUrl);
        String appStoreResponseBody = appStoreResponse.getBody();
        ItunesReceipt receiptObj = new ItunesReceipt();
        try {
            if(itunesReceiptType.equals(ItunesReceiptType.SEVEN)) {
                receiptObj = mapper.readValue(appStoreResponseBody, ItunesReceipt.class);
            }
            else{
                // Handling for type six receipts
                JSONObject receiptFullJsonObj = (JSONObject) JSONValue.parseWithException(appStoreResponseBody);
                LatestReceiptInfo latestReceiptInfo = mapper.readValue(receiptFullJsonObj.get(LATEST_RECEIPT_INFO).toString(), LatestReceiptInfo.class);
                receiptObj.setStatus(receiptFullJsonObj.get(STATUS).toString());
                List<LatestReceiptInfo> latestReceiptInfoList = new ArrayList<>();
                latestReceiptInfoList.add(latestReceiptInfo);
                receiptObj.setLatestReceiptInfoList(latestReceiptInfoList);
            }
        } catch (Exception e) {
            logger.error("Error while parsing, itunes receipt received : {} ", appStoreResponseBody);
        }
        if (receiptObj == null || receiptObj.getStatus()==null) {
            logger.error("Receipt Object returned for response " + appStoreResponseBody + " is not complete!");
//            createErrorTransactionLog(null, TransactionEvent.SUBSCRIBE, uid, service, receipt, org.apache.commons.lang.StringUtils.EMPTY, receiptFullJsonObj.toString(), response, null);
            throw new WynkRuntimeException(PaymentErrorType.PAY001, "Failed to subscribe to itunes");
        }
        int status = Integer.parseInt(receiptObj.getStatus());
        ItunesStatusCodes code = ItunesStatusCodes.getItunesStatusCodes(status);
        if (status == 0) {
            return itunesReceiptType.getSubscriptionDetailJson(receiptObj);
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


    private ItunesIdUidMapping getItunesIdUidMappingFromTrxnId(Integer productid, String originalITunesTrxnId) {
        logger.info("fetching data with originalItunesTxnId : {} and productId : {} from ItunesIdUisMapping", originalITunesTrxnId, productid);
        return itunesIdUidDao.findByKeyProductIdAnditunesId(productid, originalITunesTrxnId);
    }
    private void saveItunesIdUidMapping(String itunesId, int productid, String uid, String receipt, ItunesReceiptType type) {
        ItunesIdUidMapping mapping = new ItunesIdUidMapping(new ItunesIdUidMapping.Key(uid, productid),itunesId, receipt, type);
        itunesIdUidDao.save(mapping);
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