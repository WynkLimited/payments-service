package in.wynk.payment.service.impl;

import com.google.common.util.concurrent.RateLimiter;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.DiscountDTO;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.dto.SubscriptionNotificationMessage;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.*;
import in.wynk.payment.core.dto.*;
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
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.revenue.commons.*;
import in.wynk.revenue.utils.JsonUtils;
import in.wynk.revenue.utils.Utils;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.apache.commons.lang3.StringUtils;
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
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.revenue.commons.Constants.*;

@Service(BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE)
public class PayUMerchantPaymentService implements IRenewalMerchantPaymentService {

  private static final Logger logger = LoggerFactory.getLogger(PayUMerchantPaymentService.class);

  @Value("${payment.merchant.payu.salt}")
  private String payUSalt;

  @Value("${payment.merchant.encKey}")
  private String encryptionKey;

  @Value("${payment.merchant.payu.key}")
  private String payUMerchantKey;

  @Value("${payment.merchant.payu.api.info}")
  private String payUInfoApiUrl;

  @Value("${payment.status.web.url}")
  private String statusWebUrl;

  @Value("${payment.merchant.payu.internal.web.url}")
  private String payUwebUrl;

  @Value("${payment.merchant.payu.internal.callback.successUrl}")
  private String payUSuccessUrl;

  @Value("${payment.merchant.payu.internal.callback.failureUrl}")
  private String payUFailureUrl;

  @Value("${payment.pooling.queue.reconciliation.name}")
  private String reconciliationQueue;

  @Value("${payment.pooling.queue.subscription.name}")
  private String subscriptionQueue;

  @Value("${payment.pooling.queue.reconciliation.sqs.producer.delayInSecond}")
  private int reconciliationMessageDelay;

  @Value("${payment.pooling.queue.subscription.sqs.producer.delayInSecond}")
  private int subscriptionMessageDelay;

  private final RestTemplate restTemplate;
  private final ISQSMessagePublisher sqsMessagePublisher;
  private final ITransactionManagerService transactionManager;
  private final IRecurringPaymentManagerService recurringPaymentManagerService;

  private final RateLimiter rateLimiter = RateLimiter.create(6.0);

  public PayUMerchantPaymentService(RestTemplate restTemplate,
                                    ITransactionManagerService transactionManager,
                                    IRecurringPaymentManagerService recurringPaymentManagerService,
                                    @Qualifier(in.wynk.queue.constant.BeanConstant.SQS_EVENT_PRODUCER) ISQSMessagePublisher sqsMessagePublisher) {
    this.restTemplate = restTemplate;
    this.transactionManager = transactionManager;
    this.sqsMessagePublisher = sqsMessagePublisher;
    this.recurringPaymentManagerService = recurringPaymentManagerService;
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
    URI returnUri = startPaymentChargingForPayU(chargingRequest);
    String encryptedParams = Utils.encrypt(returnUri.getRawQuery(), encryptionKey);

    URI finalUri;
    try {
      finalUri = new URIBuilder(returnUri).removeQuery()
              .addParameter(PAYU_CHARGING_INFO, encryptedParams)
              .build();
    } catch (Exception e) {
      throw new WynkRuntimeException(PaymentErrorType.PAY001, e);
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
        MultiValueMap<String, String> userCardDetailsRequest = buildPayUInfoRequest(PayUCommand.USER_CARD_DETAILS.getCode(), userCredentials);
        PayUUserCardDetailsResponse userCardDetailsResponse = getInfoFromPayU(userCardDetailsRequest, PayUUserCardDetailsResponse.class);
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

          TransactionDetails verificationTransactionDetails = getInfoFromPayU(buildPayUInfoRequest(PayUCommand.VERIFY_PAYMENT.getCode(), transactionDetails.getTransactionId()),
                  PayUVerificationResponse.class)
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
  public BaseResponse<ChargingStatus> status(ChargingStatusRequest chargingStatusRequest) {
    ChargingStatus chargingStatus;
    switch (chargingStatusRequest.getFetchStrategy()) {
      case DIRECT_SOURCE_EXTERNAL_WITHOUT_CACHE:
        chargingStatus = fetchChargingStatusFromPayUSource(chargingStatusRequest);
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

  private ChargingStatus fetchChargingStatusFromPayUSource(ChargingStatusRequest chargingStatusRequest) {
    TransactionStatus finalTransactionStatus;
    final Transaction transaction = transactionManager.get(chargingStatusRequest.getTransactionId());
    if (transaction.getStatus() != TransactionStatus.SUCCESS) {
      MultiValueMap<String, String> payUChargingVerificationRequest = this.buildPayUInfoRequest(PayUCommand.VERIFY_PAYMENT.getCode(), chargingStatusRequest.getTransactionId());
      PayUVerificationResponse payUChargingVerificationResponse = this.getInfoFromPayU(payUChargingVerificationRequest, PayUVerificationResponse.class);
      TransactionDetails transactionDetails = payUChargingVerificationResponse.getTransactionDetails().get(chargingStatusRequest.getTransactionId());

      if (payUChargingVerificationResponse.getStatus() == 1) {
        if (SUCCESS.equalsIgnoreCase(transactionDetails.getStatus())) {
          transaction.setExitTime(Calendar.getInstance());
          finalTransactionStatus = TransactionStatus.SUCCESS;
          if(transactionDetails.getPayUUdf1().equalsIgnoreCase(PAYU_SI_KEY)) {
            Calendar nextRecurringDateTime = Calendar.getInstance();
            nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, chargingStatusRequest.getPackPeriod().getValidity());
            recurringPaymentManagerService.addRecurringPayment(transaction.getId().toString(), nextRecurringDateTime);
          }
        } else if (FAILURE.equalsIgnoreCase(transactionDetails.getStatus()) || PAYU_STATUS_NOT_FOUND.equalsIgnoreCase(transactionDetails.getStatus())) {
          transaction.setExitTime(Calendar.getInstance());
          finalTransactionStatus = TransactionStatus.FAILURE;
        } else if (chargingStatusRequest.getChargingTimestamp().getTime() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3 &&
                StringUtils.equalsIgnoreCase(PaymentConstants.PENDING, transactionDetails.getStatus())) {
          finalTransactionStatus = TransactionStatus.INPROGRESS;
        } else if (chargingStatusRequest.getChargingTimestamp().getTime() < System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3 &&
                StringUtils.equalsIgnoreCase(PaymentConstants.PENDING, transactionDetails.getStatus())) {
          transaction.setExitTime(Calendar.getInstance());
          finalTransactionStatus = TransactionStatus.FAILURE;
        } else {
          finalTransactionStatus = TransactionStatus.UNKNOWN;
        }
      } else {
        transaction.setExitTime(Calendar.getInstance());
        finalTransactionStatus = TransactionStatus.FAILURE;
      }

      transaction.setMerchantTransaction(MerchantTransaction.builder()
              .externalTransactionId(transactionDetails.getPayUExternalTxnId())
              .request(payUChargingVerificationRequest)
              .response(payUChargingVerificationResponse)
              .build());

      if (finalTransactionStatus == TransactionStatus.FAILURE) {
        if (!StringUtils.isEmpty(transactionDetails.getErrorCode()) || !StringUtils.isEmpty(transactionDetails.getErrorMessage())) {
          transaction.setPaymentError(PaymentError.builder()
                  .code(transactionDetails.getErrorCode())
                  .description(transactionDetails.getErrorMessage())
                  .build());
        }
      }

      transaction.setStatus(finalTransactionStatus);
      transactionManager.upsert(transaction);
    } else {
      finalTransactionStatus = TransactionStatus.FAILUREALREADYSUBSCRIBED;
      logger.info(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is already processed successfully for transactionId {}", chargingStatusRequest.getTransactionId());
    }

    if (finalTransactionStatus == TransactionStatus.INPROGRESS) {
      logger.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for transactionId {}", chargingStatusRequest.getTransactionId());
      throw new WynkRuntimeException(PaymentErrorType.PAY004);
    } else if (finalTransactionStatus == TransactionStatus.UNKNOWN) {
      logger.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status from payu end for transactionId {}", chargingStatusRequest.getTransactionId());
      throw new WynkRuntimeException(PaymentErrorType.PAY003);
    }

    return ChargingStatus.builder()
            .transactionStatus(finalTransactionStatus)
            .build();
  }

  private ChargingStatus fetchChargingStatusFromDataSource(ChargingStatusRequest chargingStatusRequest) {
    String transactionId =  getValueFromSession(SessionKeys.WYNK_TRANSACTION_ID);
    Transaction transaction = transactionManager.get(transactionId);
    return ChargingStatus.builder()
            .transactionStatus(transaction.getStatus())
            .build();
  }

  private URI startPaymentChargingForPayU(ChargingRequest chargingRequest) {
    String udf1 = StringUtils.EMPTY;
    String reqType = PaymentRequestType.DEFAULT.name();
    final PlanDTO selectedPlan = getSelectedPlan(chargingRequest.getPlanId());
    final float finalPlanAmount = getFinalPlanAmountToBePaid(selectedPlan);
    final Transaction transaction = initialiseTransaction(chargingRequest, finalPlanAmount);
    final String uid = getValueFromSession(SessionKeys.UID);
    final String email = uid + BASE_USER_EMAIL;

    if (!selectedPlan.getPlanType().equals(PlanType.ONE_TIME_SUBSCRIPTION)) {
      reqType = PaymentRequestType.SUBSCRIBE.name();
      udf1 = PAYU_SI_KEY.toUpperCase();
    }

    final String checksumHash = getChecksumHashForPayment(transaction.getId(), udf1, email, uid, selectedPlan.getTitle(), finalPlanAmount);

    final String userCredentials = payUMerchantKey + COLON + uid;
    final List<String> jsonCardsList = getUserCards(userCredentials);

    URI redirectUrl;

    try {
      final URIBuilder uriBuilder = new URIBuilder(payUwebUrl).addParameter(PAYU_REQUEST_TYPE, reqType)
              .addParameter(PAYU_MERCHANT_KEY, payUMerchantKey)
              .addParameter(PAYU_REQUEST_TRANSACTION_ID, transaction.getId().toString())
              .addParameter(PAYU_TRANSACTION_AMOUNT, String.valueOf(finalPlanAmount))
              .addParameter(PAYU_PRODUCT_INFO, selectedPlan.getTitle())
              .addParameter(PAYU_CUSTOMER_FIRSTNAME, uid)
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
      putValueInSession(SessionKeys.SELECTED_PLAN_ID, selectedPlan.getId());
      putValueInSession(SessionKeys.PAYMENT_CODE, PaymentCode.PAYU);

      publishSQSMessage(reconciliationQueue,
              reconciliationMessageDelay,
              PaymentReconciliationMessage.builder()
                      .uid(uid)
                      .planId(selectedPlan.getId())
                      .paymentCode(PaymentCode.PAYU)
                      .transactionId(transaction.getId().toString())
                      .transactionEvent(TransactionEvent.SUBSCRIBE)
                      .initTimestamp(Calendar.getInstance().getTime())
                      .packPeriod(selectedPlan.getPeriod())
                      .build());

    } catch (Exception e) {
      if (e instanceof WynkRuntimeException) {
        throw (WynkRuntimeException) e;
      } else {
        throw new WynkRuntimeException(PaymentErrorType.PAY002, e);
      }
    }
    return redirectUrl;
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
            .productId(chargingRequest.getPlanId())
            .amount(amount)
            .initTime(Calendar.getInstance())
            .consent(Calendar.getInstance())
            .uid(getValueFromSession(SessionKeys.UID))
            .service(getValueFromSession(SessionKeys.SERVICE))
            .msisdn(getValueFromSession(SessionKeys.MSISDN))
            .paymentChannel(PaymentCode.PAYU)
            .status(TransactionStatus.INPROGRESS)
            .type(TransactionEvent.SUBSCRIBE)
            .build());
  }

  private List<String> getUserCards(String userCredentials) {
    MultiValueMap<String, String> userCardDetailsRequest = buildPayUInfoRequest(PayUCommand.USER_CARD_DETAILS.getCode(), userCredentials);
    PayUUserCardDetailsResponse userCardDetailsResponse = getInfoFromPayU(userCardDetailsRequest, PayUUserCardDetailsResponse.class);
    return userCardDetailsResponse.getUserCards()
            .entrySet()
            .parallelStream()
            .map(cardEntry -> {
              CardDetails cardDetails = cardEntry.getValue();
              CardInfo cardInfo = getInfoFromPayU(buildPayUInfoRequest(PayUCommand.CARD_BIN_INFO.getCode(),
                      cardDetails.getCardBin()),
                      CardInfo.class);
              cardDetails.setIssuingBank(String.valueOf(cardInfo.getIssuingBank()));
              return JsonUtils.GSON.toJson(cardDetails);
            })
            .collect(Collectors.toList());
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
    String hash = generateHashForPayUApi(PayUCommand.SI_TRANSACTION.getCode(), variable);
    MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
    requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
    requestMap.add(PAYU_COMMAND, PayUCommand.SI_TRANSACTION.getCode());
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
                  PaymentLoggingMarker.PAYU_RENEWAL_STATUS_ERROR,
                  "Socket timeout but valid for reconciliation for request : {} due to {}",
                  requestMap,
                  e.getMessage(),
                  e);
        } else if (e.getRootCause() instanceof ConnectTimeoutException) {
          timeOut = true;
          logger.error(
                  PaymentLoggingMarker.PAYU_RENEWAL_STATUS_ERROR,
                  "Connection timeout but valid for reconciliation for request : {} due to {}",
                  requestMap,
                  e.getMessage(),
                  e);
        } else {
          throw new WynkRuntimeException(PaymentErrorType.PAY009, e);
        }
      } else {
        throw new WynkRuntimeException(PaymentErrorType.PAY009, e);
      }
    }
    PayURenewalResponse paymentResponse = JsonUtils.GSON.fromJson(response, PayURenewalResponse.class);
    if (paymentResponse == null) {
      paymentResponse = new PayURenewalResponse();
    }
    paymentResponse.setTimeOutFlag(timeOut);
    return paymentResponse;
  }

  private <T> T getInfoFromPayU(MultiValueMap<String, String> request, Class<T> target) {
    String response = restTemplate.postForObject(payUInfoApiUrl, request, String.class);
    return JsonUtils.GSON.fromJson(response, target);
  }

  private MultiValueMap<String, String> buildPayUInfoRequest(String command, String var1) {
    String hash = generateHashForPayUApi(command, var1);
    MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
    requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
    requestMap.add(PAYU_COMMAND, command);
    requestMap.add(PAYU_HASH, hash);
    requestMap.add(PAYU_VARIABLE1, var1);
    return requestMap;
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

  private URI processCallback(CallbackRequest<Map<String, Object>> callbackRequest) {
    try {
      String uid = getValueFromSession(SessionKeys.UID);
      String transactionId = getValueFromSession(SessionKeys.WYNK_TRANSACTION_ID).toString();

      PlanDTO selectedPlan = getSelectedPlan(getValueFromSession(SessionKeys.SELECTED_PLAN_ID));
      PayUCallbackRequestPayload payUCallbackRequestPayload = JsonUtils.GSON.fromJson(JsonUtils.GSON.toJsonTree(callbackRequest.getBody()), PayUCallbackRequestPayload.class);

      String errorCode = payUCallbackRequestPayload.getError();
      String errorMessage = payUCallbackRequestPayload.getErrorMessage();

      TransactionStatus finalTransactionStatus = TransactionStatus.UNKNOWN;
      final String status = payUCallbackRequestPayload.getStatus();
      final boolean isValidHash = validateCallbackChecksum(transactionId,
              payUCallbackRequestPayload.getStatus(),
              payUCallbackRequestPayload.getUdf1(),
              payUCallbackRequestPayload.getEmail(),
              payUCallbackRequestPayload.getFirstName(),
              selectedPlan.getTitle(),
              getFinalPlanAmountToBePaid(selectedPlan),
              payUCallbackRequestPayload.getResponseHash());

      if (!isValidHash) {
        logger.error(PaymentLoggingMarker.PAYU_CHARGING_CALLBACK_FAILURE,
                "Invalid checksum found with transactionStatus: {}, Wynk transactionId: {}, PayU transactionId: {}, Reason: {} for uid: {}",
                payUCallbackRequestPayload.getStatus(),
                transactionId,
                payUCallbackRequestPayload.getExternalTransactionId(),
                errorMessage,
                uid);
      }

      final URIBuilder returnUrl = new URIBuilder(statusWebUrl);

      if (isValidHash && !StringUtils.isEmpty(status)) {
        if (FAILURE.equalsIgnoreCase(status)) {
          returnUrl
                  .addParameter(STATUS, FAILURE)
                  .addParameter(PAYU_BSYS_PARAM, FAILURE)
                  .addParameter(MESSAGE, TRANSACTION_FAILED);
          finalTransactionStatus = TransactionStatus.FAILURE;
        } else if (SUCCESS.equalsIgnoreCase(status)) {
          returnUrl
                  .addParameter(STATUS, SUCCESS)
                  .addParameter(PAYU_BSYS_PARAM, SUCCESS)
                  .addParameter(MESSAGE, SUCCESS);
          finalTransactionStatus = TransactionStatus.SUCCESS;
        } else if (PENDING.equalsIgnoreCase(status)) {
          returnUrl
                  .addParameter(STATUS, INPROGRESS)
                  .addParameter(PAYU_BSYS_PARAM, INPROGRESS)
                  .addParameter(MESSAGE, INPROGRESS);
          finalTransactionStatus = TransactionStatus.INPROGRESS;
        }
      } else {
        returnUrl
                .addParameter(STATUS, FAILURE)
                .addParameter(PAYU_BSYS_PARAM, FAILURE)
                .addParameter(MESSAGE, TRANSACTION_FAILED);
        finalTransactionStatus = TransactionStatus.FAILURE;
      }

      returnUrl.addParameter(TRANSACTION_ID, transactionId);
      returnUrl.addParameter(SESSION_ID, SessionContextHolder.get().getId().toString());

      Transaction transaction = transactionManager.get(transactionId);

      if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
        transaction.setMerchantTransaction(MerchantTransaction.builder()
                .externalTransactionId(payUCallbackRequestPayload.getExternalTransactionId())
                .request(payUCallbackRequestPayload)
                .response(returnUrl.build())
                .build());
        transaction.setStatus(finalTransactionStatus);

        if (finalTransactionStatus == TransactionStatus.SUCCESS) {
          transaction.setExitTime(Calendar.getInstance());
          publishSQSMessage(subscriptionQueue,
                  subscriptionMessageDelay,
                  SubscriptionNotificationMessage.builder()
                          .planId(selectedPlan.getId())
                          .transactionId(transactionId)
                          .transactionEvent(transaction.getType())
                          .transactionStatus(transaction.getStatus())
                          .build());
          if(selectedPlan.getPlanType() == PlanType.SUBSCRIPTION) {
            Calendar nextRecurringDateTime = Calendar.getInstance();
            nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, selectedPlan.getPeriod().getValidity());
            recurringPaymentManagerService.addRecurringPayment(transactionId, nextRecurringDateTime);
          }
        } else {
          transaction.setPaymentError(PaymentError.builder()
                  .code(errorCode)
                  .description(errorMessage)
                  .build());
        }
        transactionManager.upsert(transaction);
      }

      return returnUrl.build();
    } catch (Exception e) {
      throw new WynkRuntimeException(PaymentErrorType.PAY006, e);
    }
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

  private boolean validateCallbackChecksum(String transactionId, String transactionStatus, String udf1, String email, String firstName, String planTitle, float amount, String payUResponseHash) {
    String generatedString =
            payUSalt
                    + PIPE_SEPARATOR
                    + transactionStatus
                    + "||||||||||"
                    + udf1
                    + PIPE_SEPARATOR
                    + email
                    + PIPE_SEPARATOR
                    + firstName
                    + PIPE_SEPARATOR
                    + planTitle
                    + PIPE_SEPARATOR
                    + amount
                    + PIPE_SEPARATOR
                    + transactionId
                    + PIPE_SEPARATOR
                    + payUMerchantKey;
    final String generatedHash = EncryptionUtils.generateSHA512Hash(generatedString);
    assert generatedHash != null;
    return generatedHash.equals(payUResponseHash);
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
