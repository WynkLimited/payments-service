package in.wynk.payment.service.impl;

import com.google.gson.JsonObject;
import java.net.URI;
import java.net.URLDecoder;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.dto.enums.PhonePeTransactionStatus;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.PhonePeTransactionResponse;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.revenue.commons.utils.Utils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import static in.wynk.payment.constant.PhonePeConstant.*;

@Service(BeanConstant.PHONEPE_MERCHANT_PAYMENT_SERVICE)
public class PhonePeMerchantPaymentService implements IRenewalMerchantPaymentService {

    @Autowired
    private RestTemplate phonePeRestTemplate;

    @Value("${phonepe.merchant.id}")
    private String merchantId = "WYNK";

    @Value("${phonepe.callback.url}")
    private String phonePeCallBackURL;

    @Value("${phonepe.api.base.url}")
    private String phonePeBaseUrl;

    @Value("${phonepe.salt}")
    private String salt;

    @Value("${phonepe.return.url}")
    private String returnWynkUrl;

    private final String prefixStatusApi = "/v3/transaction/" + merchantId + "/";

    private final String debitCall = "/v4/debit";


    private Logger logger = LoggerFactory.getLogger(PhonePeMerchantPaymentService.class.getCanonicalName());

    @Override
    public <T> BaseResponse<T> handleCallback(CallbackRequest callbackRequest) {
        try {
            String requestPayload = (String) callbackRequest.getBody();
            Map<String, List<String>> payloadParams = Utils.getParamsFromRequestPayload(requestPayload);
            PhonePeTransactionResponse phonePeTransactionResponse = new PhonePeTransactionResponse(payloadParams);
            boolean validChecksum = validateChecksum(requestPayload, Utils.getStringParameter(payloadParams, "checksum"));
            URI returnUrl = getCallbackRedirectionUri(phonePeTransactionResponse, callbackRequest.getTransactionId(), validChecksum);
            return new BaseResponse(returnUrl.toString(), HttpStatus.OK, HttpHeaders.EMPTY);
        }
        catch(Exception e){
            throw new WynkRuntimeException(e);
        }
    }

    @Override
    public <T> BaseResponse<T> doCharging(ChargingRequest chargingRequest) {
        try {
            PhonePePaymentRequest phonePePaymentRequest = new PhonePePaymentRequest(chargingRequest);
            phonePePaymentRequest.setAmount(chargingRequest.getAmount());
            phonePePaymentRequest.setMerchantId(merchantId);
            HttpEntity<String> requestEnity = getRequestEntity(phonePePaymentRequest);
            URI redirectUri = getRedirectionUri(requestEnity);
            return new BaseResponse(redirectUri.toString(), HttpStatus.OK, null);
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
    public <T> BaseResponse<T> status(ChargingStatusRequest chargingStatusRequest) {
        PhonePeTransactionResponse phonePeTransactionStatusResponse = getTransactionStatus(chargingStatusRequest.getTransactionId());
        // TODO convert to Reconciliation message
        return new BaseResponse(phonePeTransactionStatusResponse, HttpStatus.OK, null);
    }

    private URI getCallbackRedirectionUri(PhonePeTransactionResponse phonePeTransactionResponse, String transactionId, boolean validChecksum) {
        try {
            URIBuilder returnUrl = new URIBuilder(phonePeCallBackURL);
            if (validChecksum && phonePeTransactionResponse.getCode() != null) {
                if (PhonePeTransactionStatus.PAYMENT_SUCCESS.equals(phonePeTransactionResponse.getCode())) {
                    addParamToUri(SUCCESS, SUCCESS, returnUrl);
                    // TODO call subscription service
                } else if (PhonePeTransactionStatus.PAYMENT_PENDING.equals(phonePeTransactionResponse.getCode())) {
                    addParamToUri(INPROGRESS, INPROGRESS, returnUrl);
                } else {
                    addParamToUri(FAILURE, FAILURE, returnUrl);
                }
            } else {
                addParamToUri(FAILURE, FAILURE, returnUrl);
            }
            returnUrl.addParameter(TRANSACTION_ID, transactionId);

            // TODO - Handle PointDownload Transaction Event Handling
            return returnUrl.build();
        }
        catch(Throwable th) {
            throw new WynkRuntimeException(th);
        }
    }

    private void addParamToUri(String status, String message, URIBuilder uri){
        uri.addParameter(STATUS, status);
        uri.addParameter(BSYS, status);
        uri.addParameter(MESSAGE, message);
    }

    private HttpEntity<String> getRequestEntity(PhonePePaymentRequest phonePePaymentRequest){
        try {
            String requestJson = Utils.getGson().toJson(phonePePaymentRequest);
            JsonObject jsonRequest = new JsonObject();
            if (StringUtils.isNotEmpty(requestJson)) {
                jsonRequest.addProperty("request", Utils.encodeBase64(requestJson));
            }
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
            URI uri = phonePeRestTemplate.postForLocation(phonePeBaseUrl + debitCall, request);
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
        String suffixStatusApi = "/status";
        String apiPath = prefixStatusApi + transactionId + suffixStatusApi;
        String xVerifyHeader = apiPath + salt;
        xVerifyHeader = DigestUtils.sha256Hex(xVerifyHeader) + "###1";
        MultiValueMap<String, String> headers = new LinkedMultiValueMap<String, String>();
        headers.add(X_VERIFY, xVerifyHeader);
        headers.add(CONTENT_TYPE, "application/json");
        HttpEntity entity = new HttpEntity(headers);
        ResponseEntity<PhonePeTransactionResponse> responseEntity = phonePeRestTemplate.exchange(phonePeBaseUrl + apiPath, HttpMethod.GET, entity, PhonePeTransactionResponse.class, new HashMap<>());
        PhonePeTransactionResponse phonePeTransactionResponse = responseEntity.getBody();
        if (phonePeTransactionResponse != null && phonePeTransactionResponse.getCode() != null) {
            logger.info("PhonePe txn response for transaction Id {} :: {}", transactionId, phonePeTransactionResponse);
        }
        return phonePeTransactionResponse;
    }

    private boolean validateChecksum(String query, String checksum) {
        boolean validated = false;
        StringBuilder validationString = new StringBuilder();
        if (StringUtils.isNotEmpty(query)) {
            try {
                for (String param : query.split("&")) {
                    String pair[] = param.split("=");
                    String key = URLDecoder.decode(pair[0], "UTF-8");
                    String value = "";
                    if (pair.length > 1) {
                        value = URLDecoder.decode(pair[1], "UTF-8");
                    }
                    if (!key.equalsIgnoreCase("checksum"))
                        validationString.append(value);
                }
                String calculatedChecksum = DigestUtils.sha256Hex(validationString + salt) + "###1";
                if (StringUtils.equals(checksum, calculatedChecksum)) {
                    validated = true;
                }
            } catch(Exception e) {
                logger.error("Exception while Checksum validation ", e);
            }
        }
        return validated;
    }
}
