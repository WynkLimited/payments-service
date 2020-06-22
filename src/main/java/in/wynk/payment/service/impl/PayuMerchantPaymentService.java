package in.wynk.payment.service.impl;

import com.google.common.collect.ImmutableMap;
import in.wynk.payment.constant.BeanConstant;
import in.wynk.payment.constant.PaymentConstants;
import in.wynk.payment.dto.CardDetails;
import in.wynk.payment.dto.TransactionDetails;
import in.wynk.payment.dto.response.PayUUserCardDetailsResponse;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.PayUVerificationResponse;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.revenue.commons.EncryptionUtils;
import in.wynk.revenue.utils.JsonUtils;
import in.wynk.revenue.utils.Utils;
import in.wynk.utils.common.PaymentRequestType;
import in.wynk.utils.constant.PackType;
import in.wynk.utils.domain.SubscriptionPack;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.http.client.utils.URIBuilder;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.net.URI;
import java.text.ParseException;
import java.util.*;

import static in.wynk.payment.constant.PaymentConstants.*;

@Service(BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE)
public class PayuMerchantPaymentService implements IRenewalMerchantPaymentService {

  private static final Logger logger = LoggerFactory.getLogger(PayuMerchantPaymentService.class);

  @Autowired private RestTemplate restTemplate;

  private String payUMerchantKey;

  private String payUSalt;

  private String cards;

  private String payUInfoApiUrl;

  private String payUUrl;

  private String payUSuccessUrl;

  private String payUFailureUrl;

  private static final FastDateFormat DATE_FORMAT =
      FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");

  @Override
  public <T> BaseResponse<T> handleCallback(CallbackRequest callbackRequest) {
    return null;
  }

  @Override
  public <T> BaseResponse<T> doCharging(ChargingRequest chargingRequest) {
    String isICICI = StringUtils.EMPTY; // Keeping this empty for future use.
    SubscriptionPack pack = new SubscriptionPack(); // Get pack from partnerProductId.
    if (pack.getPackageType().equals(PackType.ONE_TIME_SUBSCRIPTION)) {
      startPaymentForPayU(pack, chargingRequest);
    }
    return null;
  }

  @Override
  public <T> BaseResponse<T> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
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
    if (!StringUtils.isEmpty(transactionDetails.getPayUUdf1())){
      paymentMetadata.put(SUBSID, transactionDetails.getPayUExternalTxnId());
      paymentMetadata.put(PAYU_CARD_NUMBER, transactionDetails.getResponseCardNumber());
    }
    /*
    SubscriptionPack pack = subscriptionPacksCachingService.getPackOfType(PaymentMethod.PAY_U, pollingMessage.getPartnerProductId());
    // fix for payu renew issue - susbsId not find, need to change for try and buy
    Subscription sub = subscriptionService.getSubscriptionForPartnerProductId(pollingMessage.getService(), pollingMessage.getUid(), pollingMessage.getPartnerProductId());
    */

    return null;
  }

  private void startPaymentForPayU(SubscriptionPack pack, ChargingRequest chargingRequest) {
    try {
      String firstName = PaymentConstants.DEFAULT_FIRST_NAME;
      String email = StringUtils.EMPTY;
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
      URIBuilder uriBuilder =
          new URIBuilder(payUUrl)
              .addParameter("rt", reqType)
              .addParameter(PAYU_MERCHANT_KEY, payUMerchantKey)
              .addParameter(PAYU_REQUEST_TRANSACTION_ID, chargingRequest.getTransactionId())
              .addParameter(
                  PAYU_TRANSACTION_AMOUNT,
                  String.valueOf(pack.getPricing().getRoundedPriceInRupees()))
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
        URI redirectUrl = uriBuilder.addParameter(PAYU_ENFORCE_PAYMENT, "netbanking").build();
      } else {
        URI redirectUrl = uriBuilder.addParameter(PAYU_SI, "1").build();
      }
      // TODO : insert into polling queue.
    } catch (Throwable throwable) {
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
    double amount = pack.getPricing().getRoundedPriceInRupees();
    String productTitle = pack.getTitle();
    String finalString =
        payUMerchantKey
            + PIPE_SEPARATOR
            + chargingRequest.getTransactionId()
            + PIPE_SEPARATOR
            + String.valueOf(amount)
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

  private JSONObject getInfoFromPayU(String command, String var1) {
    String hash = generateHashForPayUApi(command, var1);
    Map<String, String> requestMap =
        ImmutableMap.of(
            PAYU_MERCHANT_KEY,
            payUMerchantKey,
            PAYU_COMMAND,
            command,
            PAYU_HASH,
            hash,
            PAYU_VARIABLE1,
            var1);
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
}
