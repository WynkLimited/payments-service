package in.wynk.payment.service.impl;

import com.google.common.util.concurrent.RateLimiter;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.constant.PaymentConstants;
import in.wynk.payment.constant.PaymentErrorCode;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dto.CardInfo;
import in.wynk.payment.core.dto.PaymentReconciliationMessage;
import in.wynk.payment.core.dto.Transaction;
import in.wynk.payment.dto.CardDetails;
import in.wynk.payment.dto.PayUCallbackRequestPayload;
import in.wynk.payment.dto.TransactionDetails;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.PayURenewalResponse;
import in.wynk.payment.dto.response.PayUUserCardDetailsResponse;
import in.wynk.payment.dto.response.PayUVerificationResponse;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.revenue.commons.*;
import in.wynk.revenue.utils.JsonUtils;
import in.wynk.revenue.utils.Utils;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.FastDateFormat;
import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.client.utils.URLEncodedUtils;
import org.apache.http.conn.ConnectTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
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

import static in.wynk.payment.constant.PaymentConstants.*;
import static in.wynk.revenue.commons.Constants.*;

@Service(BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE)
public class PayuMerchantPaymentService implements IRenewalMerchantPaymentService {

  private static final Logger logger = LoggerFactory.getLogger(PayuMerchantPaymentService.class);

  @Value("${payment.pooling.queue.reconciliation.sqs.messages.producer.delayInSecond}")
  private int sqsMessageDelay;

  @Value("${payment.merchant.payu.key}")
  private String payUMerchantKey;

  @Value("${payment.merchant.payu.salt}")
  private String payUSalt;

  @Value("${payment.merchant.payu.api.info}")
  private String payUInfoApiUrl;

  @Value("${payment.merchant.payu.internal.web.url}")
  private String webUrl;

  @Value("${payment.merchant.payu.internal.callback.successUrl}")
  private String payUSuccessUrl;

  @Value("${payment.merchant.payu.internal.callback.failureUrl}")
  private String payUFailureUrl;

  @Value("${payment.merchant.encKey}")
  private String encryptionKey;

  @Value("${payment.pooling.queue.reconciliation.name}")
  private String reconciliationQueue;

  private final RestTemplate restTemplate;
  private final ISQSMessagePublisher sqsMessagePublisher;
  private final ITransactionManagerService transactionManager;

  private final RateLimiter rateLimiter = RateLimiter.create(6.0);

  private static final FastDateFormat DATE_FORMAT = FastDateFormat.getInstance("yyyy-MM-dd HH:mm:ss");

  public PayuMerchantPaymentService(RestTemplate restTemplate,
                                    ITransactionManagerService transactionManager,
                                    @Qualifier(in.wynk.queue.constant.BeanConstant.SQS_EVENT_PRODUCER) ISQSMessagePublisher sqsMessagePublisher) {
    this.restTemplate = restTemplate;
    this.transactionManager = transactionManager;
    this.sqsMessagePublisher = sqsMessagePublisher;
  }

  @Override
  public BaseResponse<String> handleCallback(CallbackRequest callbackRequest) {
    URI returnUrl = processCallback(callbackRequest);
    HttpHeaders httpHeaders = new HttpHeaders();
    httpHeaders.add("ct", "text/plain; charset=UTF-8");
    httpHeaders.add(HttpHeaders.LOCATION, returnUrl.toString());
    httpHeaders.add(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate");
    httpHeaders.add(HttpHeaders.PRAGMA, "no-cache");
    httpHeaders.add(HttpHeaders.EXPIRES, String.valueOf(0));
    return BaseResponse.<String>builder()
            .status(HttpStatus.FOUND)
            .headers(httpHeaders)
            .body(returnUrl.toString())
            .build();
  }

  @Override
  public BaseResponse<Map<String, String>> doCharging(ChargingRequest chargingRequest) {
    // any use case for icici bank ?
    URI returnUri = startPaymentForPayU(chargingRequest);
    String encryptedParams = Utils.encrypt(returnUri.getRawQuery(), encryptionKey);

    URI finalUri;
    try {
      finalUri = new URIBuilder(returnUri).removeQuery().addParameter(PAYU_CHARGING_INFO, encryptedParams).build();
    } catch (Exception e) {
      throw new WynkRuntimeException(PaymentErrorCode.PAY001);
    }

    Map<String, String> queryParams = URLEncodedUtils.parse(finalUri, Charset.defaultCharset())
                                                     .stream()
                                                     .collect(Collectors.toMap(NameValuePair::getName, NameValuePair::getValue));

    HttpHeaders httpHeaders = new HttpHeaders();
    setContentTypeHeader(httpHeaders, JsonUtils.GSON.toJson(queryParams));

    return BaseResponse.<Map<String, String>>builder()
                       .body(queryParams)
                       .status(HttpStatus.OK)
                       .headers(httpHeaders)
                       .build();
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
    try {
      if (StringUtils.isEmpty(paymentRenewalRequest.getCardToken())) {
        String userCredentials = payUMerchantKey + COLON + paymentRenewalRequest.getUid();
        PayUUserCardDetailsResponse userCardDetailsResponse = getUserCardsFromPayu(userCredentials);
        Map<String, String> numberTokenMap =
                userCardDetailsResponse.getUserCards().values().stream()
                        .collect(Collectors.toMap(CardDetails::getCardNo, CardDetails::getCardToken));
        paymentRenewalRequest.setCardToken(
            numberTokenMap.get(paymentRenewalRequest.getCardNumber()));
      }
      if (StringUtils.isEmpty(paymentRenewalRequest.getSubsId())) {
        throw new WynkRuntimeException("No subId found for Subscription");
      }
      boolean status = false;
      String errorMessage = StringUtils.EMPTY;
      //      String amount = stringBuilder.append(trLog.getAmount()).append("0").toString();
      PayURenewalResponse payURenewalResponse = getPayURenewalStatus(paymentRenewalRequest);
      if (payURenewalResponse.isTimeOutFlag()) {
        status = true;
      } else {
        TransactionDetails transactionDetails =
            payURenewalResponse.getDetails().get(paymentRenewalRequest.getId());
        errorMessage = transactionDetails.getErrorMessage();
        if (transactionDetails.getStatus().equals(PAYU_STATUS_CAPTURED)) {
          status = true;

          TransactionDetails verificationTransactionDetails = getPayUTransactionStatus(transactionDetails.getTransactionId())
                  .getTransactionDetails()
                  .get(paymentRenewalRequest.getId());

          errorMessage = verificationTransactionDetails.getErrorMessage();
        } else if (transactionDetails.getStatus().equals(PAYU_SI_STATUS_FAILURE)) {
          errorMessage = transactionDetails.getPayUResponseFailureMessage();
        } else if (transactionDetails.getStatus().equals(SUCCESS)) {
          status = true;
        }
      }
    } catch (Throwable throwable) {
      logger.error("Exception while parsing acknowledgement response.", throwable);
    } finally {
      //      TODO: Create subscription renewal charging response and return it.
    }
    return null;
  }

  @Override
  public <T> BaseResponse<T> status(ChargingStatusRequest chargingStatusRequest) {
    PayUVerificationResponse verificationResponse = getPayUTransactionStatus(chargingStatusRequest.getOrderId());
    TransactionDetails transactionDetails = verificationResponse.getTransactionDetails().get(chargingStatusRequest.getOrderId());
    try {
      // getPollingMessage(chargingStatusRequest, transactionDetails);
    } catch (Exception e) {

    }
    return null;
  }

  private URI startPaymentForPayU(ChargingRequest chargingRequest) {
    PlanDTO selectedPlan = getSelectedPlan(chargingRequest.getPlanId());
    Transaction transaction = initialiseTransaction(chargingRequest, selectedPlan);
    String uid = getValueFromSession(SessionKeys.UID);
    String firstName = uid;
    String udf1 = StringUtils.EMPTY;
    String email = firstName + BASE_USER_EMAIL;
    String reqType = PaymentRequestType.DEFAULT.name();

    if (!selectedPlan.getPlanType().equals(PlanType.ONE_TIME_SUBSCRIPTION)) {
      reqType = PaymentRequestType.SUBSCRIBE.name();
      udf1 = PAYU_SI_KEY.toUpperCase();
    }

    String checksumHash = getChecksumHashForPayment(transaction.getId(), udf1, email, firstName, selectedPlan.getTitle(), selectedPlan.getPrice().getAmount());

    String userCredentials = payUMerchantKey + COLON + firstName;
    List<String> jsonCardsList = getUserCards(userCredentials);

    URI redirectUrl;

    try {
      URIBuilder uriBuilder = new URIBuilder(webUrl).addParameter(PAYU_REQUEST_TYPE, reqType)
                                                     .addParameter(PAYU_MERCHANT_KEY, payUMerchantKey)
                                                     .addParameter(PAYU_REQUEST_TRANSACTION_ID, transaction.getId().toString())
                                                     .addParameter(PAYU_TRANSACTION_AMOUNT, String.valueOf(selectedPlan.getPrice().getAmount()))
                                                     .addParameter(PAYU_PRODUCT_INFO, selectedPlan.getTitle())
                                                     .addParameter(PAYU_CUSTOMER_FIRSTNAME, firstName)
                                                     .addParameter(PAYU_CUSTOMER_EMAIL, email)
                                                     .addParameter(PAYU_CUSTOMER_MSISDN, Utils.getTenDigitMsisdn(chargingRequest.getMsisdn()))
                                                     .addParameter(PAYU_HASH, checksumHash)
                                                     .addParameter(PAYU_SUCCESS_URL, payUSuccessUrl.replace(SID_KEY, SessionContextHolder.get().getId().toString()))
                                                     .addParameter(PAYU_FAILURE_URL, payUFailureUrl.replace(SID_KEY, SessionContextHolder.get().getId().toString()))
                                                     .addParameter(PAYU_ENFORCE_PAY_METHOD, chargingRequest.getEnforcePayment())
                                                     .addParameter(PAYU_UDF1_PARAMETER, udf1)
                                                     .addParameter(IS_FALLBACK_ATTEMPT, String.valueOf(false))
                                                     .addParameter(ERROR, PAYU_REDIRECT_MESSAGE)
                                                     .addParameter(CARD_DETAILS, jsonCardsList.toString())
                                                     .addParameter(PAYU_PG, chargingRequest.getPg())
                                                     .addParameter(PAYU_USER_CREDENTIALS, userCredentials);

      if (selectedPlan.getPlanType().equals(PlanType.ONE_TIME_SUBSCRIPTION))
        redirectUrl = uriBuilder.addParameter(PAYU_ENFORCE_PAYMENT, NETBANKING_MODE).build();
      else
        redirectUrl = uriBuilder.addParameter(PAYU_SI_KEY, "1").build();

      putValueInSession(SessionKeys.WYNK_TRANSACTION_ID, transaction.getId());

      PaymentReconciliationMessage message = PaymentReconciliationMessage.builder()
                                                                         .uid(uid)
                                                                         .planId(selectedPlan.getId())
                                                                         .transactionId(transaction.getId().toString())
                                                                         .build();

      sqsMessagePublisher.publish(SendSQSMessageRequest.<PaymentReconciliationMessage>builder()
                                                       .queueName(reconciliationQueue)
                                                       .delaySeconds(sqsMessageDelay)
                                                       .message(message)
                                                       .build());

    } catch (Exception e) {
      if(e instanceof WynkRuntimeException) {
        throw (WynkRuntimeException) e;
      } else {
        throw new WynkRuntimeException(PaymentErrorCode.PAY002, e);
      }
    }
    return redirectUrl;
  }

  private Transaction initialiseTransaction(ChargingRequest chargingRequest, PlanDTO selectedPlan) {
    return transactionManager.upsert(Transaction.builder()
            .productId(chargingRequest.getPlanId())
            .amount(selectedPlan.getPrice().getAmount())
            .initTime(Calendar.getInstance())
            .consent(Calendar.getInstance())
            .uid(getValueFromSession(SessionKeys.UID))
            .service(getValueFromSession(SessionKeys.SERVICE))
            .paymentChannel(PaymentCode.PAYU.name())
            .status(TransactionStatus.INPROGRESS.name())
            .type(TransactionEvent.SUBSCRIBE.getValue())
            .build());
  }

  private List<String> getUserCards(String userCredentials) {
    PayUUserCardDetailsResponse payUUserCardDetailsResponse = getUserCardsFromPayu(userCredentials);
    return payUUserCardDetailsResponse.getUserCards()
                                      .entrySet()
                                      .parallelStream()
                                      .map(cardEntry -> {
                                            CardDetails cardDetails = cardEntry.getValue();
                                            CardInfo cardInfo = checkCardIsDomestic(cardDetails.getCardBin());
                                            cardDetails.setIssuingBank(String.valueOf(cardInfo.getIssuingBank()));
                                            return JsonUtils.GSON.toJson(cardDetails); })
                                      .collect(Collectors.toList());
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
          Map<String, String> initParams = JsonUtils.GSON.fromJson(jsonPayload, Map.class);
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

  private PayUUserCardDetailsResponse getUserCardsFromPayu(String userCredentials) {
    String response = getInfoFromPayU("get_user_cards", userCredentials);
    return JsonUtils.GSON.fromJson(response, PayUUserCardDetailsResponse.class);
  }

  private CardInfo checkCardIsDomestic(String cardBin) {
    String response = getInfoFromPayU("check_isDomestic", cardBin);
    return JsonUtils.GSON.fromJson(response, CardInfo.class);
  }

  private PayUVerificationResponse getPayUTransactionStatus(String orderId) {
    String response = getInfoFromPayU("verify_payment", orderId);
    return JsonUtils.GSON.fromJson(response, PayUVerificationResponse.class);
  }

  private PayURenewalResponse getPayURenewalStatus(PaymentRenewalRequest paymentRenewalRequest) {
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
    try {
      response = restTemplate.postForObject(payUInfoApiUrl, requestMap, String.class);
    } catch (RestClientException e) {
      if (e.getRootCause() != null) {
        if (e.getRootCause() instanceof SocketTimeoutException) {
          timeOut = true;
          logger.error(
                  "Socket timedout but valid for reconcillation for request : {}",
                  requestMap,
                  e.getMessage(),
                  e);
        } else if (e.getRootCause() instanceof ConnectTimeoutException) {
          timeOut = true;
          logger.error(
                  "Connection timedout but valid for reconcillation for request : {}",
                  requestMap,
                  e.getMessage(),
                  e);
        } else {
          throw new WynkRuntimeException("Exception occurred while making SI payment on Payu");
        }
      } else {
        throw new WynkRuntimeException("Exception occurred while making SI payment on Payu");
      }
    }
    PayURenewalResponse paymentResponse = JsonUtils.GSON.fromJson(response, PayURenewalResponse.class);
    if (paymentResponse == null) {
      paymentResponse = new PayURenewalResponse();
    }
    paymentResponse.setTimeOutFlag(timeOut);
    return paymentResponse;
  }

  private String getInfoFromPayU(String command, String var1) {
    String hash = generateHashForPayUApi(command, var1);
    MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<String, String>();
    requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
    requestMap.add(PAYU_COMMAND, command);
    requestMap.add(PAYU_HASH, hash);
    requestMap.add(PAYU_VARIABLE1, var1);
    String response = restTemplate.postForObject(payUInfoApiUrl, requestMap, String.class);
    return response;
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
      PayUCallbackRequestPayload payUCallbackRequestPayload = JsonUtils.GSON.fromJson(JsonUtils.GSON.toJsonTree(callbackRequest.getBody()), PayUCallbackRequestPayload.class);

      URIBuilder returnUrl = new URIBuilder();

      String errorMessage = payUCallbackRequestPayload.getError();
      if (StringUtils.isEmpty(errorMessage)) {
        errorMessage = payUCallbackRequestPayload.getErrorMessage();
      }

      boolean isValidHash = validateCallbackChecksum(payUCallbackRequestPayload, callbackRequest);
      String status = payUCallbackRequestPayload.getStatus();

      if (!isValidHash) {
        logger.info(
            "PayU Response, txnStatus:{}, transactionId: {}, PayU TXNID: {}, Reason: {}",
                payUCallbackRequestPayload.getStatus(),
                callbackRequest.getId(),
                payUCallbackRequestPayload.getExternalTransactionId(),
            errorMessage);
      }
      if (isValidHash && !StringUtils.isEmpty(status)) {
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

  private String getChecksumHashForPayment(UUID transactionId, String udf1, String email, String firstName, String planTitle, float amount) {
    String rawChecksum = payUMerchantKey
                                        + PIPE_SEPARATOR
                                        + transactionId.toString()
                                        + PIPE_SEPARATOR
                                        + amount
                                        + PIPE_SEPARATOR
                                        + planTitle
                                        + PIPE_SEPARATOR
                                        + firstName
                                        + PIPE_SEPARATOR
                                        + email
                                        + PIPE_SEPARATOR
                                        + udf1
                                        + "||||||||||"
                                        + payUSalt;
    return EncryptionUtils.generateSHA512Hash(rawChecksum);
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

  private static void setContentTypeHeader(HttpHeaders httpHeaders, String responseStr) {
    String contentType = TEXT_PLAIN;
    if (JsonUtils.isDefinitelyJson(responseStr)) {
      contentType = APPLICATION_JSON;
    }
    httpHeaders.add("ct", contentType);
  }

}
