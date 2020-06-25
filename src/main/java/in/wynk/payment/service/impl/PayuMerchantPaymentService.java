package in.wynk.payment.service.impl;

import com.google.common.util.concurrent.RateLimiter;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.constant.PaymentConstants;
import in.wynk.payment.dto.CardDetails;
import in.wynk.payment.dto.PayUCallbackRequestPayload;
import in.wynk.payment.dto.TransactionDetails;
import in.wynk.payment.dto.response.PayURenewalResponse;
import in.wynk.payment.dto.response.PayUUserCardDetailsResponse;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.PayUVerificationResponse;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.revenue.commons.*;
import in.wynk.revenue.utils.JsonUtils;
import in.wynk.revenue.utils.Utils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ConnectTimeoutException;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.charset.Charset;
import java.text.DecimalFormat;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.payment.constant.Constants.ONE_DAY_IN_MILLI;
import static in.wynk.payment.constant.PaymentConstants.*;

@Service(BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE)
public class PayuMerchantPaymentService implements IRenewalMerchantPaymentService {

  private static final Logger logger = LoggerFactory.getLogger(PayuMerchantPaymentService.class);

  @Autowired private RestTemplate restTemplate;

  private String payUMerchantKey = "aU2Uoi";

  private String payUSalt = "6vbnfVtw";

  private String payUInfoApiUrl = "https://info.payu.in/merchant/postservice.php?form=2";

  private String payUTVWebUrl = "https://wcfpay-new.wynk.in/atv/payu-atv";

  private String payUWebUrl = "https://wcfpay-new.wynk.in/wcf/payu";

  private String payUSuccessUrl = "https://capi.wynk.in/wynk/v1/payucallback/payment";

  private String payUFailureUrl = "https://capi.wynk.in/wynk/v1/payucallback/payment";

  private String encryptionKey = "BSB$PORTAL@2014#";

  private final RateLimiter rateLimiter = RateLimiter.create(6.0);

  private static final FastDateFormat DATE_FORMAT =
      FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");

  @Override
  public <T> BaseResponse<T> handleCallback(CallbackRequest callbackRequest) {
    URI returnUrl = processCallback(callbackRequest);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("ct","text/plain; charset=UTF-8");
    httpHeaders.add(HttpHeaders.LOCATION, returnUrl.toString());
    httpHeaders.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
    httpHeaders.add(HttpHeaders.PRAGMA, "no-cache");
    httpHeaders.add(HttpHeaders.EXPIRES, String.valueOf(0));
    return (BaseResponse<T>) BaseResponse.builder().status(HttpStatus.FOUND).headers(httpHeaders).body(returnUrl).build();
  }

  @Override
  public <T> BaseResponse<T> doCharging(ChargingRequest chargingRequest) {
    String isICICI = StringUtils.EMPTY; // Keeping this empty for future use.
    SubscriptionPack pack = new SubscriptionPack(); // Get pack from partnerProductId.
    URI returnUri = startPaymentForPayU(pack, chargingRequest);
    URI finalUri = Utils.encryptUrl(returnUri, encryptionKey);
    Map<String, String> queryParams = URLEncodedUtils.parse(finalUri, Charset.defaultCharset()).stream().collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));
    HttpHeaders httpHeaders = new HttpHeaders();
    Utils.setContentTypeHeader(httpHeaders, JsonUtils.GSON.toJson(queryParams));
    return (BaseResponse<T>) BaseResponse.builder().body(queryParams).status(HttpStatus.OK).headers(httpHeaders).build();
  }

  @Override
  public <T> BaseResponse<T> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
    //    Subscription subscription =
    // subscriptionService.getSubscriptionForPartnerProductId(renewalMessage.getService(),
    // renewalMessage.getUid(), renewalMessage.getPartnerProductId());
    //    SubscriptionPack pack =
    // subscriptionPacksCachingService.getPackByProductId(subscription.getProductId());
    if (StringUtils.isEmpty(paymentRenewalRequest.getPaidPartnerProductId())) {
      //      pack = subscriptionPacksCachingService.getPackOfType(PaymentMethod.PAY_U,
      // paidPartnerProductId);
    }
    /*
        Map<String, String> paymentMetadata = subscription.getPaymentMetaData();
        String subsId = paymentMetadata.get(PaymentConstants.SUBSID);
        String cardToken = paymentMetadata.get(PaymentConstants.PAYU_RESPONSE_CARDTOKEN);
        String cardNumber = paymentMetadata.get(PaymentConstants.PAYU_RESPONSE_CARDNUMBER);
    */
    try{
    if (StringUtils.isEmpty(paymentRenewalRequest.getCardToken())) {
      String userCredentials = payUMerchantKey + COLON + paymentRenewalRequest.getUid();
      PayUUserCardDetailsResponse userCardDetailsResponse =
          JsonUtils.GSON.fromJson(
              getUserCards(userCredentials).toJSONString(), PayUUserCardDetailsResponse.class);
      Map<String, String> numberTokenMap =
          userCardDetailsResponse.getUserCards().values().stream()
              .collect(Collectors.toMap(CardDetails::getCardNo, CardDetails::getCardToken));
      paymentRenewalRequest.setCardToken(numberTokenMap.get(paymentRenewalRequest.getCardNumber()));
    }
    if (StringUtils.isEmpty(paymentRenewalRequest.getSubsId())) {
      throw new WynkRuntimeException("No subId found for Subscription");
    }
    boolean status = false;
    String errorMessage = StringUtils.EMPTY;
    //      String amount = stringBuilder.append(trLog.getAmount()).append("0").toString();
    JSONObject paymentResponse = getPayURenewalStatus(paymentRenewalRequest);
    PayURenewalResponse payURenewalResponse = JsonUtils.GSON.fromJson(paymentResponse.toJSONString(), PayURenewalResponse.class);
    if (payURenewalResponse.isTimeOutFlag()){
      status = true;
    }
    else {
      TransactionDetails transactionDetails = payURenewalResponse.getDetails().get(paymentRenewalRequest.getId());
      errorMessage = transactionDetails.getErrorMessage();
      if (transactionDetails.getStatus().equals(PAYU_STATUS_CAPTURED)){
        status = true;
        paymentResponse = getPayUTransactionStatus(transactionDetails.getTransactionId());
        TransactionDetails verificationTransactionDetails = JsonUtils.GSON.fromJson(paymentResponse.toJSONString(), PayUVerificationResponse.class).getTransactionDetails().get(paymentRenewalRequest.getId());
        errorMessage = verificationTransactionDetails.getErrorMessage();
      }
      else if (transactionDetails.getStatus().equals(PAYU_SI_STATUS_FAILURE)){
        errorMessage = transactionDetails.getPayUResponseFailureMessage();
      }
      else if (transactionDetails.getStatus().equals(SUCCESS)){
        status = true;
      }
    }
    }catch (Throwable throwable){
      logger.error("Exception while parsing acknowledgement response.", throwable);
  }
  finally{
//      TODO: Create subscription renewal charging response and return it.
    }
    return null;
  }

  @Override
  public <T> BaseResponse<T> status(ChargingStatusRequest chargingStatusRequest) {
    PayUVerificationResponse verificationResponse =
        JsonUtils.GSON.fromJson(
            getPayUTransactionStatus(chargingStatusRequest.getOrderId()).toJSONString(),
            PayUVerificationResponse.class);
    TransactionDetails transactionDetails =
        verificationResponse.getTransactionDetails().get(chargingStatusRequest.getOrderId());
    try {
      //      getPollingMessage(chargingStatusRequest, transactionDetails);
    } catch (Exception e) {
    }
    return null;
  }

  private URI startPaymentForPayU(SubscriptionPack pack, ChargingRequest chargingRequest) {
    try {
      String firstName = PaymentConstants.DEFAULT_FIRST_NAME;
      String email = "test@wynk.in";
      String uid = ""; // uid from msisdn.
      /*
      if (!WynkService.NOT_WCF_INTEGRATED_SERVICES.contains(chargingRequest.getService()))
          UserProfileData user = userDAO.getUserByUid(uid); // for name and email
      else{
        jsonPayload in transaction log. for username and email
      }
      */
      // Pair<String,String> user = getNameAndEmail();
      String udf1 = StringUtils.EMPTY;
      String reqType = PaymentRequestType.DEFAULT.name();
      if (!pack.getPackageType().equals(PackType.ONE_TIME_SUBSCRIPTION)) {
        reqType = PaymentRequestType.SUBSCRIBE.name();
        udf1 = "SI";
      }
      String checksumHash =
          getChecksumHashForPayment(pack, chargingRequest, udf1, email, firstName);
      String userCredentials = payUMerchantKey + COLON + uid;
      PayUUserCardDetailsResponse payUUserCardDetailsResponse =
          JsonUtils.GSON.fromJson(
              getUserCards(userCredentials).toJSONString(), PayUUserCardDetailsResponse.class);
      List<String> jsonCardsList = new ArrayList<>();
      for (Map.Entry<String, CardDetails> entry :
          payUUserCardDetailsResponse.getUserCards().entrySet()) {
        CardDetails cardDetails = entry.getValue();
        JSONObject cardInfo = checkCardIsDomestic(cardDetails.getCardBin());
        cardDetails.setIssuingBank(String.valueOf(cardInfo.get("issuingBank")));
        jsonCardsList.add(JsonUtils.GSON.toJson(cardDetails));
      }
      URI redirectUrl = new URI("");
      String payUUrl = payUWebUrl;
      if (chargingRequest.getService().equals("airteltv")){
        payUUrl = payUTVWebUrl;
      }
      URIBuilder uriBuilder =
          new URIBuilder(payUUrl)
              .addParameter("rt", reqType)
              .addParameter(PAYU_MERCHANT_KEY, payUMerchantKey)
              .addParameter(PAYU_REQUEST_TRANSACTION_ID, chargingRequest.getTransactionId())
              .addParameter(
                  PAYU_TRANSACTION_AMOUNT,
                  pack.getPricing())
              .addParameter(PAYU_PRODUCT_INFO, pack.getTitle())
              .addParameter(PAYU_CUSTOMER_FIRSTNAME, firstName)
              .addParameter(PAYU_CUSTOMER_EMAIL, email)
              .addParameter(
                  PAYU_CUSTOMER_MSISDN, Utils.getTenDigitMsisdn(chargingRequest.getMsisdn()))
              .addParameter(PAYU_HASH, checksumHash)
              .addParameter(PAYU_SUCCESS_URL, payUSuccessUrl)
              .addParameter(PAYU_FAILURE_URL, payUFailureUrl)
              .addParameter(PAYU_ENFORCE_PAY_METHOD, chargingRequest.getEnforcePayment())
              .addParameter(PAYU_UDF1_PARAMETER, udf1)
              .addParameter(IS_FALLBACK_ATTEMPT, String.valueOf(false))
              .addParameter(ERROR, PAYU_REDIRECT_MESSAGE)
              .addParameter(CARD_DETAILS, jsonCardsList.toString())
              .addParameter(PAYU_PG, chargingRequest.getPg())
              .addParameter(PAYU_USER_CREDENTIALS, userCredentials);
      if (pack.getPackageType().equals(PackType.ONE_TIME_SUBSCRIPTION)) {
        redirectUrl = uriBuilder.addParameter(PAYU_ENFORCE_PAYMENT, "netbanking").build();
      } else {
        redirectUrl = uriBuilder.addParameter(PAYU_SI, "1").build();
      }
      return redirectUrl;
      // TODO : insert into polling queue.
    } catch (Throwable throwable) {
      logger.error("Error in startPaymentForPayU {}", throwable);
      throw new WynkRuntimeException("Error in startPaymentForPayU");
    }
  }
  // To be checked.
  /*private Pair<String, String> getNameAndEmail(){
    String firstName= "";
    String email= "";
    if (!WynkService.NOT_WCF_INTEGRATED_SERVICES.contains(trLog.getService())) {
      UserProfileData user = userDAO.getUserByUid(uid);
      if (!StringUtils.isBlank(user.getName())) {
        StringTokenizer st = new StringTokenizer(user.getName());
        firstName = st.nextToken(" ");
      }
      email = user.getEmail();
    } else {
      String jsonPayload = trLog.getJsonPayload();
      if (!StringUtils.isBlank(jsonPayload)) {
        try {
          Map<String, String> initParams = Constants.GSON.fromJson(jsonPayload, Map.class);
          if (initParams.containsKey("userName")) {
            String userName = initParams.get("userName");
            if (StringUtils.isNotBlank(userName)) {
              StringTokenizer st = new StringTokenizer(userName);
              firstName = st.nextToken(" ");
            }
          }
          if (initParams.containsKey("userEmail")) {
            email = initParams.get("userEmail");
          }
        } catch (Throwable th) {
          logger.error(LoggingMarkers.PG_PAYU_ERROR, "Exception while converting jsonPayload to map : {} : {}", jsonPayload, th.getMessage(), th);
        }
      }

    }
    return new Pair<String,String>(firstName, email);
  }*/
  private String getChecksumHashForPayment(
      SubscriptionPack pack,
      ChargingRequest chargingRequest,
      String udf1,
      String email,
      String firstName) {
    String amount = pack.getPricing();
    String productTitle = pack.getTitle();
    String finalString =
        payUMerchantKey
            + PIPE_SEPARATOR
            + chargingRequest.getTransactionId()
            + PIPE_SEPARATOR
            + amount
            + PIPE_SEPARATOR
            + productTitle
            + PIPE_SEPARATOR
            + firstName
            + PIPE_SEPARATOR
            + email
            + PIPE_SEPARATOR
            + udf1
            + "||||||||||"
            + payUSalt;
    return EncryptionUtils.generateSHA512Hash(finalString);
  }

  private JSONObject getUserCards(String userCredentials) {
    return getInfoFromPayU("get_user_cards", userCredentials);
  }

  private JSONObject checkCardIsDomestic(String cardBin) {
    return getInfoFromPayU("check_isDomestic", cardBin);
  }

  private JSONObject getPayUTransactionStatus(String orderId) {
    return getInfoFromPayU("verify_payment", orderId);
  }

  private JSONObject getPayURenewalStatus(PaymentRenewalRequest paymentRenewalRequest) {
    LinkedHashMap<String, String> orderedMap = new LinkedHashMap<>();
    String userCredentials = payUMerchantKey + COLON + paymentRenewalRequest.getUid();
    orderedMap.put(PAYU_RESPONSE_AUTH_PAYUID, paymentRenewalRequest.getSubsId());
    orderedMap.put(PAYU_TRANSACTION_AMOUNT, paymentRenewalRequest.getAmount());
    orderedMap.put(PAYU_REQUEST_TRANSACTION_ID, paymentRenewalRequest.getTransactionId());
    orderedMap.put(PAYU_USER_CREDENTIALS, userCredentials);
    orderedMap.put(PAYU_CARD_TOKEN, paymentRenewalRequest.getCardToken());
    String variable = JsonUtils.GSON.toJson(orderedMap);
    String hash = generateHashForPayUApi("si_transaction", variable);
    MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<String, String>();
    requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
    requestMap.add(PAYU_COMMAND, "si_transaction");
    requestMap.add(PAYU_HASH, hash);
    requestMap.add(PAYU_VARIABLE1, variable);
    String response = null;
    boolean timeOut = false;
    rateLimiter.acquire();
    try{
      response = restTemplate.postForObject(payUInfoApiUrl, requestMap, String.class);
    }
    catch (RestClientException e) {
      if(e.getRootCause() != null) {
        if(e.getRootCause() instanceof SocketTimeoutException) {
          timeOut = true;
          logger.error("Socket timedout but valid for reconcillation for request : {}", requestMap, e.getMessage(), e);
        }
        else if(e.getRootCause() instanceof ConnectTimeoutException) {
          timeOut = true;
          logger.error("Connection timedout but valid for reconcillation for request : {}", requestMap, e.getMessage(), e);
        }
        else {
          throw new WynkRuntimeException("Exception occurred while making SI payment on Payu");
        }
      }
      else {
        throw new WynkRuntimeException("Exception occurred while making SI payment on Payu");
      }
    }
    JSONObject paymentResponse = JsonUtils.getJsonObjectFromPayload(response);
    if (paymentResponse == null){
      paymentResponse = new JSONObject();
    }
    paymentResponse.put("timeOutFlag", timeOut);
    return paymentResponse;
  }

  private JSONObject getInfoFromPayU(String command, String var1) {
    String hash = generateHashForPayUApi(command, var1);
    MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<String, String>();
    requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
    requestMap.add(PAYU_COMMAND, command);
    requestMap.add(PAYU_HASH, hash);
    requestMap.add(PAYU_VARIABLE1, var1);
    String response = restTemplate.postForObject(payUInfoApiUrl, requestMap, String.class);
    return JsonUtils.getJsonObjectFromPayload(response);
  }

  private String generateHashForPayUApi(String command, String var1) {
    String finalString =
        payUMerchantKey
            + PIPE_SEPARATOR
            + command
            + PIPE_SEPARATOR
            + var1
            + PIPE_SEPARATOR
            + payUSalt;
    return EncryptionUtils.generateSHA512Hash(finalString);
  }

  private void getPollingMessage(
      ChargingStatusRequest chargingStatusRequest, TransactionDetails transactionDetails)
      throws Exception {
    long externalChargingDate = System.currentTimeMillis();
    if (!StringUtils.isEmpty(transactionDetails.getPayUTransactionDate())) {
      try {
        Date date = DATE_FORMAT.parse(transactionDetails.getPayUTransactionDate());
        externalChargingDate = date.getTime();
      } catch (ParseException e) {
        logger.error(
            "Exception while parsing date: {}", transactionDetails.getPayUTransactionDate(), e);
      }
    }
    Map<String, String> paymentMetadata = new HashMap<>();
    if (!StringUtils.isEmpty(transactionDetails.getPayUUdf1())) {
      paymentMetadata.put(SUBSID, transactionDetails.getPayUExternalTxnId());
      paymentMetadata.put(PAYU_CARD_NUMBER, transactionDetails.getResponseCardNumber());
    }
    // TODO : Need to understand the flow here.
    SubscriptionPack pack = new SubscriptionPack();
    /*
    SubscriptionPack pack = subscriptionPacksCachingService.getPackOfType(PaymentMethod.PAY_U, pollingMessage.getPartnerProductId());
    // fix for payu renew issue - susbsId not find, need to change for try and buy
    Subscription sub = subscriptionService.getSubscriptionForPartnerProductId(pollingMessage.getService(), pollingMessage.getUid(), pollingMessage.getPartnerProductId());
    if(sub != null && sub.getPaymentMetaData() != null) {
      paymentMetadata = sub.getPaymentMetaData();
    }
    */
    SubscriptionStatus subscriptionStatus = null;
    long validTillDate = 0;
    TransactionStatus transactionStatus = TransactionStatus.UNKNOWN;
    if (Integer.parseInt(transactionDetails.getStatus()) == 1) {
      if (SUCCESS.equalsIgnoreCase(transactionDetails.getStatus())) {
        subscriptionStatus = SubscriptionStatus.ACTIVE;
        validTillDate = externalChargingDate + pack.getPackPeriod();
        transactionStatus = TransactionStatus.SUCCESS;
      } else if (FAILURE.equalsIgnoreCase(transactionDetails.getStatus())
          || PAYU_STATUS_NOT_FOUND.equalsIgnoreCase(transactionDetails.getStatus())) {
        subscriptionStatus = SubscriptionStatus.DEACTIVATED;
        transactionStatus = TransactionStatus.FAILURE;
      } else if (chargingStatusRequest.getCreatedTimeStamp().getTime()
              > System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3
          && StringUtils.equalsIgnoreCase(
              PaymentConstants.PENDING, transactionDetails.getStatus())) {
        throw new Exception("Still pending from payU side.");
      } else if (chargingStatusRequest.getCreatedTimeStamp().getTime()
              < System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3
          && StringUtils.equalsIgnoreCase(
              PaymentConstants.PENDING, transactionDetails.getStatus())) {
        subscriptionStatus = SubscriptionStatus.DEACTIVATED;
        transactionStatus = TransactionStatus.FAILURE;
      } else {
        throw new Exception("No matching status found for payU renewal.");
      }
    } else {
      subscriptionStatus = SubscriptionStatus.DEACTIVATED;
      transactionStatus = TransactionStatus.FAILURE;
    }
    TransactionEvent event = TransactionEvent.RENEW;
    if (TransactionEvent.UNKNOWN != chargingStatusRequest.getTransactionEvent()) {
      event = chargingStatusRequest.getTransactionEvent();
    }
    //    TODO : Return pojo in the format of polling message.
  }

  private URI processCallback(CallbackRequest callbackRequest) {
    try {
      // Create POJO from response payload.
      PayUCallbackRequestPayload callbackRequestPayload =
          JsonUtils.GSON.fromJson(
              JsonUtils.GSON.toJsonTree((Map<String, Object>)callbackRequest.getBody()), PayUCallbackRequestPayload.class);
      URIBuilder returnUrl = new URIBuilder(callbackRequest.getReturnUrl());
      String errorMessage = callbackRequestPayload.getError();
      if (StringUtils.isEmpty(errorMessage)) {
        errorMessage = callbackRequestPayload.getErrorMessage();
      }
      boolean validHash = validateCallbackChecksum(callbackRequestPayload, callbackRequest);
      String status = callbackRequestPayload.getStatus();
      if (!validHash){
        logger.info("PayU Response, txnStatus:{}, transactionId: {}, PayU TXNID: {}, Reason: {}", callbackRequestPayload.getStatus(), callbackRequest.getId(), callbackRequestPayload.getExternalTransactionId(), errorMessage);
      }
      if (validHash && !StringUtils.isEmpty(status)) {
        if (FAILURE.equalsIgnoreCase(status)) {
          returnUrl
              .addParameter(STATUS, FAILURE)
              .addParameter("bsys", FAILURE)
              .addParameter(MESSAGE, TRANSACTION_FAILED);
        } else if (SUCCESS.equalsIgnoreCase(status)) {
          returnUrl
              .addParameter(STATUS, SUCCESS)
              .addParameter("bsys", SUCCESS)
              .addParameter(MESSAGE, SUCCESS);
          // TODO : Create subscription part.
        } else {
          returnUrl
              .addParameter(STATUS, INPROGRESS)
              .addParameter("bsys", INPROGRESS)
              .addParameter(MESSAGE, INPROGRESS);
        }
      } else {
        returnUrl
            .addParameter(STATUS, FAILURE)
            .addParameter("bsys", FAILURE)
            .addParameter(MESSAGE, TRANSACTION_FAILED);
      }
      returnUrl.addParameter(TRANSACTION_ID, callbackRequest.getTransactionId());
      /*if (TransactionEvent.POINTDOWNLOAD.equals(callbackRequest.getTransactionEvent())) {
        Utils.populatePointDownloadReturnUrl(returnUrl); // How to add parameters.
      }*/
      // Insert or update transaction log and end transaction part.
      return returnUrl.build();
    } catch (Throwable th) {
      logger.error("Error in processCallback {}", th);
      throw new WynkRuntimeException("Error in processCallback");
    }
  }

  private boolean validateCallbackChecksum(
      PayUCallbackRequestPayload callbackRequestPayload, CallbackRequest callbackRequest) {
    DecimalFormat df = new DecimalFormat("#.00");
    String amount = df.format(Double.valueOf(callbackRequest.getAmount()));
    String generatedString =
        payUSalt
            + PIPE_SEPARATOR
            + callbackRequestPayload.getStatus()
            + "||||||||||"
            + callbackRequestPayload.getUdf1()
            + PIPE_SEPARATOR
            + callbackRequestPayload.getEmail()
            + PIPE_SEPARATOR
            + callbackRequestPayload.getFirstName()
            + PIPE_SEPARATOR
            + callbackRequest.getTitle()
            + PIPE_SEPARATOR
            + amount
            + PIPE_SEPARATOR
            + callbackRequest.getId()
            + PIPE_SEPARATOR
            + payUMerchantKey;
    String generatedHash = EncryptionUtils.generateSHA512Hash(generatedString);
    return generatedHash.equals(callbackRequestPayload.getResponseHash());
  }
}
