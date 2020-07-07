package in.wynk.payment.service.impl;

import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import in.wynk.commons.constants.SessionKeys;
import in.wynk.commons.dto.DiscountDTO;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SessionDTO;
import in.wynk.commons.enums.PaymentRequestType;
import in.wynk.commons.enums.PlanType;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.commons.utils.EncryptionUtils;
import in.wynk.commons.utils.Utils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PayUCommand;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.PaymentError;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.VerificationType;
import in.wynk.payment.dto.payu.CardDetails;
import in.wynk.payment.dto.payu.PayUCallbackRequestPayload;
import in.wynk.payment.dto.payu.PayUCardInfo;
import in.wynk.payment.dto.payu.PayUTransactionDetails;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatus;
import in.wynk.payment.dto.response.PayuVpaVerificationResponse;
import in.wynk.payment.dto.response.payu.PayURenewalResponse;
import in.wynk.payment.dto.response.payu.PayUUserCardDetailsResponse;
import in.wynk.payment.dto.response.payu.PayUVerificationResponse;
import in.wynk.payment.service.*;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.ConnectTimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.time.Duration;
import java.util.Calendar;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static in.wynk.commons.constants.Constants.ONE_DAY_IN_MILLI;
import static in.wynk.payment.core.constant.PaymentConstants.*;

@Service(BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE)
public class PayUMerchantPaymentService implements IRenewalMerchantPaymentService, IMerchantVerificationService {

    private static final Logger logger = LoggerFactory.getLogger(PayUMerchantPaymentService.class);
    private final RestTemplate restTemplate;
    private final ISQSMessagePublisher sqsMessagePublisher;
    private final ITransactionManagerService transactionManager;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;
    private final RateLimiter rateLimiter = RateLimiter.create(6.0);

    @Value("${payment.merchant.payu.salt}")
    private String payUSalt;
    @Value("${payment.encKey}")
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
    @Value("${payment.pooling.queue.reconciliation.sqs.producer.delayInSecond}")
    private int reconciliationMessageDelay;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    @Autowired
    private Gson gson;

    public PayUMerchantPaymentService(RestTemplate restTemplate,
                                      ITransactionManagerService transactionManager,
                                      IRecurringPaymentManagerService recurringPaymentManagerService,
                                      @Qualifier(in.wynk.queue.constant.BeanConstant.SQS_EVENT_PRODUCER) ISQSMessagePublisher sqsMessagePublisher,
                                      ISubscriptionServiceManager subscriptionServiceManager) {
        this.restTemplate = restTemplate;
        this.transactionManager = transactionManager;
        this.sqsMessagePublisher = sqsMessagePublisher;
        this.subscriptionServiceManager = subscriptionServiceManager;
        this.recurringPaymentManagerService = recurringPaymentManagerService;
    }

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        URI returnUrl = processCallback(callbackRequest);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.add(HttpHeaders.LOCATION, returnUrl.toString());
        httpHeaders.add(HttpHeaders.CACHE_CONTROL, CacheControl.maxAge(Duration.ZERO).mustRevalidate().getHeaderValue());
        httpHeaders.add(HttpHeaders.PRAGMA, CacheControl.noCache().getHeaderValue());
        httpHeaders.add(HttpHeaders.EXPIRES, String.valueOf(0));
        return BaseResponse.<Void>builder()
                .status(HttpStatus.FOUND)
                .headers(httpHeaders)
                .build();
    }

    @Override
    public BaseResponse<Map<String, String>> doCharging(ChargingRequest chargingRequest) {
        Map<String, String> payUpayload = startPaymentChargingForPayU(chargingRequest);
        String encryptedParams = null;
        try {
            encryptedParams = EncryptionUtils.encrypt(gson.toJson(payUpayload), encryptionKey);
        } catch (Exception e) {
            logger.error(BaseLoggingMarkers.ENCRYPTION_ERROR, e.getMessage(), e);
            throw new WynkRuntimeException(e);
        }
        Map<String, String> queryParams = new HashMap<>();
        queryParams.put(PAYU_CHARGING_INFO, encryptedParams);

        return BaseResponse.<Map<String, String>>builder()
                .body(queryParams)
                .status(HttpStatus.OK)
                .build();
    }

    @Override
    public <T> BaseResponse<T> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
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
            PayURenewalResponse payURenewalResponse = doChargingForRenewal(paymentRenewalRequest);
            if (payURenewalResponse.isTimeOutFlag()) {
                status = true;
            } else {
                PayUTransactionDetails payUTransactionDetails =
                        payURenewalResponse.getDetails().get(paymentRenewalRequest.getId());
                errorMessage = payUTransactionDetails.getErrorMessage();
                if (payUTransactionDetails.getStatus().equals(PAYU_STATUS_CAPTURED)) {
                    status = true;

                    PayUTransactionDetails verificationPayUTransactionDetails = getInfoFromPayU(buildPayUInfoRequest(PayUCommand.VERIFY_PAYMENT.getCode(), payUTransactionDetails.getTransactionId()),
                            PayUVerificationResponse.class)
                            .getTransactionDetails()
                            .get(paymentRenewalRequest.getId());

                    errorMessage = verificationPayUTransactionDetails.getErrorMessage();
                } else if (payUTransactionDetails.getStatus().equals(PAYU_SI_STATUS_FAILURE)) {
                    errorMessage = payUTransactionDetails.getPayUResponseFailureMessage();
                } else if (payUTransactionDetails.getStatus().equals(SUCCESS)) {
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
        TransactionStatus existingTransactionStatus;
        TransactionStatus finalTransactionStatus;

        Transaction transaction = transactionManager.get(chargingStatusRequest.getTransactionId());

        existingTransactionStatus = transaction.getStatus();
        fetchAndUpdateTransactionFromSource(transaction);
        finalTransactionStatus = transaction.getStatus();

        if (existingTransactionStatus != TransactionStatus.SUCCESS && finalTransactionStatus == TransactionStatus.SUCCESS) {
            subscriptionServiceManager.publish(chargingStatusRequest.getPlanId(),
                    chargingStatusRequest.getUid(),
                    chargingStatusRequest.getTransactionId(),
                    finalTransactionStatus,
                    chargingStatusRequest.getTransactionEvent());
        } else if (existingTransactionStatus == TransactionStatus.SUCCESS && finalTransactionStatus == TransactionStatus.FAILURE) {
            subscriptionServiceManager.publish(chargingStatusRequest.getPlanId(),
                    chargingStatusRequest.getUid(),
                    chargingStatusRequest.getTransactionId(),
                    finalTransactionStatus,
                    TransactionEvent.UNSUBSCRIBE);
        }

        transactionManager.upsert(transaction);

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

    public void fetchAndUpdateTransactionFromSource(Transaction transaction) {
        TransactionStatus finalTransactionStatus = TransactionStatus.UNKNOWN;
        MultiValueMap<String, String> payUChargingVerificationRequest = this.buildPayUInfoRequest(PayUCommand.VERIFY_PAYMENT.getCode(), transaction.getId().toString());
        PayUVerificationResponse payUChargingVerificationResponse = this.getInfoFromPayU(payUChargingVerificationRequest, PayUVerificationResponse.class);
        PayUTransactionDetails payUTransactionDetails = payUChargingVerificationResponse.getTransactionDetails().get(transaction.getId().toString());
        if (payUChargingVerificationResponse.getStatus() == 1) {
            if (SUCCESS.equalsIgnoreCase(payUTransactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
                if (payUTransactionDetails.getPayUUdf1().equalsIgnoreCase(PAYU_SI_KEY)) {
                    Calendar nextRecurringDateTime = Calendar.getInstance();
                    PlanDTO plan = subscriptionServiceManager.getPlan(transaction.getPlanId());
                    nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, plan.getPeriod().getValidity());
                    recurringPaymentManagerService.addRecurringPayment(transaction.getId().toString(), nextRecurringDateTime);
                }
            } else if (FAILURE.equalsIgnoreCase(payUTransactionDetails.getStatus()) || PAYU_STATUS_NOT_FOUND.equalsIgnoreCase(payUTransactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3 &&
                    StringUtils.equalsIgnoreCase(PaymentConstants.PENDING, payUTransactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.INPROGRESS;
            } else if (transaction.getInitTime().getTimeInMillis() < System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3 &&
                    StringUtils.equalsIgnoreCase(PaymentConstants.PENDING, payUTransactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } else {
            finalTransactionStatus = TransactionStatus.FAILURE;
        }

        if (finalTransactionStatus == TransactionStatus.FAILURE) {
            if (!StringUtils.isEmpty(payUTransactionDetails.getErrorCode()) || !StringUtils.isEmpty(payUTransactionDetails.getErrorMessage())) {
                transaction.setPaymentError(PaymentError.builder()
                        .code(payUTransactionDetails.getErrorCode())
                        .description(payUTransactionDetails.getErrorMessage())
                        .build());
            }
        }

        transaction.setMerchantTransaction(MerchantTransaction.builder()
                .externalTransactionId(payUTransactionDetails.getPayUExternalTxnId())
                .request(payUChargingVerificationRequest)
                .response(payUChargingVerificationResponse)
                .build());

        transaction.setStatus(finalTransactionStatus.name());
    }


    private ChargingStatus fetchChargingStatusFromDataSource(ChargingStatusRequest chargingStatusRequest) {
        Transaction transaction = transactionManager.get(chargingStatusRequest.getTransactionId());
        return ChargingStatus.builder()
                .transactionStatus(transaction.getStatus())
                .build();
    }

    private Map<String, String> startPaymentChargingForPayU(ChargingRequest chargingRequest) {
        SessionDTO sessionDTO = SessionContextHolder.getBody();
        int planId = chargingRequest.getPlanId();
        String udf1 = StringUtils.EMPTY;
        String reqType = PaymentRequestType.DEFAULT.name();
        String msisdn = Utils.getTenDigitMsisdn(sessionDTO.get(SessionKeys.MSISDN));

        final PlanDTO selectedPlan = subscriptionServiceManager.getPlan(planId);
        final double finalPlanAmount = getFinalPlanAmountToBePaid(selectedPlan);
        final Transaction transaction = initialiseTransaction(chargingRequest, finalPlanAmount);
        final String uid = getValueFromSession(SessionKeys.UID);
        final String email = uid + BASE_USER_EMAIL;
        if (!selectedPlan.getPlanType().equals(PlanType.ONE_TIME_SUBSCRIPTION)) {
            reqType = PaymentRequestType.SUBSCRIBE.name();
            udf1 = PAYU_SI_KEY.toUpperCase();
        }

        String checksumHash = getChecksumHashForPayment(transaction.getId(), udf1, email, uid, String.valueOf(planId), finalPlanAmount);
        String userCredentials = payUMerchantKey + COLON + uid;
        Map<String, String> paylaod = new HashMap<>();

        paylaod.put(PAYU_REQUEST_TYPE, reqType);
        paylaod.put(PAYU_MERCHANT_KEY, payUMerchantKey);
        paylaod.put(PAYU_REQUEST_TRANSACTION_ID, transaction.getId().toString());
        paylaod.put(PAYU_TRANSACTION_AMOUNT, String.valueOf(finalPlanAmount));
        paylaod.put(PAYU_PRODUCT_INFO, selectedPlan.getTitle());
        paylaod.put(PAYU_CUSTOMER_FIRSTNAME, uid);
        paylaod.put(PAYU_CUSTOMER_EMAIL, email);
        paylaod.put(PAYU_CUSTOMER_MSISDN, msisdn);
        paylaod.put(PAYU_HASH, checksumHash);
        paylaod.put(PAYU_SUCCESS_URL, payUSuccessUrl.replace(SID_KEY, SessionContextHolder.get().getId().toString()));
        paylaod.put(PAYU_FAILURE_URL, payUFailureUrl.replace(SID_KEY, SessionContextHolder.get().getId().toString()));
//      paylaod.put(PAYU_ENFORCE_PAY_METHOD, chargingRequest.getEnforcePayment()) upto FE since it knows which option is chosen.
        paylaod.put(PAYU_UDF1_PARAMETER, udf1);
        paylaod.put(IS_FALLBACK_ATTEMPT, String.valueOf(false));
        paylaod.put(ERROR, PAYU_REDIRECT_MESSAGE);
        paylaod.put(PAYU_USER_CREDENTIALS, userCredentials);

        if (selectedPlan.getPlanType().equals(PlanType.ONE_TIME_SUBSCRIPTION)) {
            paylaod.put(PAYU_ENFORCE_PAYMENT, NETBANKING_MODE);
            //@Zuber why we are forcing option to NetBanking
        } else {
            paylaod.put(PAYU_SI_KEY, "1");
        }
        putValueInSession(SessionKeys.WYNK_TRANSACTION_ID, transaction.getId());
        putValueInSession(SessionKeys.PAYMENT_CODE, PaymentCode.PAYU);

        PaymentReconciliationMessage reconciliationMessage = new PaymentReconciliationMessage(transaction);
        publishSQSMessage(reconciliationQueue, reconciliationMessageDelay,reconciliationMessage);

        return paylaod;
    }

    private double getFinalPlanAmountToBePaid(PlanDTO selectedPlan) {
        float finalPlanAmount = selectedPlan.getPrice().getAmount();
        if (selectedPlan.getPrice().getDiscount().size() > 0) {
            for (DiscountDTO discount : selectedPlan.getPrice().getDiscount()) {
                finalPlanAmount *= ((double) (100 - discount.getPercent()) / 100);
            }
        }
        return finalPlanAmount;
    }

    private Transaction initialiseTransaction(ChargingRequest chargingRequest, double amount) {
        return transactionManager.upsert(Transaction.builder()
                .planId(chargingRequest.getPlanId())
                .amount(amount)
                .initTime(Calendar.getInstance())
                .consent(Calendar.getInstance())
                .uid(getValueFromSession(SessionKeys.UID))
                .service(getValueFromSession(SessionKeys.SERVICE))
                .msisdn(getValueFromSession(SessionKeys.MSISDN))
                .paymentChannel(PaymentCode.PAYU.name())
                .status(TransactionStatus.INPROGRESS.name())
                .type(TransactionEvent.PURCHASE.name())
                .build());
    }


    //TODO: ( on AMAN) need to use to fetch user's saved cards.
    public List<String> getUserCards(String uid) {
        String userCredentials = payUMerchantKey + COLON + uid;
        MultiValueMap<String, String> userCardDetailsRequest = buildPayUInfoRequest(PayUCommand.USER_CARD_DETAILS.getCode(), userCredentials);
        PayUUserCardDetailsResponse userCardDetailsResponse = getInfoFromPayU(userCardDetailsRequest, PayUUserCardDetailsResponse.class);
        return userCardDetailsResponse.getUserCards()
                .entrySet()
                .parallelStream()
                .map(cardEntry -> {
                    CardDetails cardDetails = cardEntry.getValue();
                    PayUCardInfo payUCardInfo = getInfoFromPayU(buildPayUInfoRequest(PayUCommand.CARD_BIN_INFO.getCode(),
                            cardDetails.getCardBin()),
                            PayUCardInfo.class);
                    cardDetails.setIssuingBank(String.valueOf(payUCardInfo.getIssuingBank()));
                    return gson.toJson(cardDetails);
                })
                .collect(Collectors.toList());
    }

    private PayURenewalResponse doChargingForRenewal(PaymentRenewalRequest paymentRenewalRequest) {
        LinkedHashMap<String, String> orderedMap = new LinkedHashMap<>();
        String userCredentials = payUMerchantKey + COLON + paymentRenewalRequest.getUid();
        orderedMap.put(PAYU_RESPONSE_AUTH_PAYUID, paymentRenewalRequest.getSubsId());
        orderedMap.put(PAYU_TRANSACTION_AMOUNT, paymentRenewalRequest.getAmount());
        orderedMap.put(PAYU_REQUEST_TRANSACTION_ID, paymentRenewalRequest.getTransactionId());
        orderedMap.put(PAYU_USER_CREDENTIALS, userCredentials);
        orderedMap.put(PAYU_CARD_TOKEN, paymentRenewalRequest.getCardToken());
        String variable = gson.toJson(orderedMap);
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
        PayURenewalResponse paymentResponse = gson.fromJson(response, PayURenewalResponse.class);
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
        final String transactionId = getValueFromSession(SessionKeys.WYNK_TRANSACTION_ID).toString();
        final Transaction transaction = transactionManager.get(transactionId);
        try {
            final String uid = transaction.getUid();

            final PlanDTO selectedPlan = subscriptionServiceManager.getPlan(transaction.getPlanId());
            final PayUCallbackRequestPayload payUCallbackRequestPayload = gson.fromJson(gson.toJsonTree(callbackRequest.getBody()), PayUCallbackRequestPayload.class);

            final String errorCode = payUCallbackRequestPayload.getError();
            final String errorMessage = payUCallbackRequestPayload.getErrorMessage();

            final boolean isValidHash = validateCallbackChecksum(transactionId,
                    payUCallbackRequestPayload.getStatus(),
                    payUCallbackRequestPayload.getUdf1(),
                    payUCallbackRequestPayload.getEmail(),
                    payUCallbackRequestPayload.getFirstName(),
                    selectedPlan.getTitle(),
                    getFinalPlanAmountToBePaid(selectedPlan),
                    payUCallbackRequestPayload.getResponseHash());

            if (isValidHash) {
                fetchAndUpdateTransactionFromSource(transaction);
                if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                    transaction.setExitTime(Calendar.getInstance());
                    subscriptionServiceManager.publish(selectedPlan.getId(), uid, transactionId, transaction.getStatus(), transaction.getType());
                    if (selectedPlan.getPlanType() == PlanType.SUBSCRIPTION) {
                        Calendar nextRecurringDateTime = Calendar.getInstance();
                        nextRecurringDateTime.add(Calendar.DAY_OF_MONTH, selectedPlan.getPeriod().getValidity());
                        recurringPaymentManagerService.addRecurringPayment(transactionId, nextRecurringDateTime);
                    }
                }
            } else {
                logger.error(PaymentLoggingMarker.PAYU_CHARGING_CALLBACK_FAILURE,
                        "Invalid checksum found with transactionStatus: {}, Wynk transactionId: {}, PayU transactionId: {}, Reason: error code: {}, error message: {} for uid: {}",
                        payUCallbackRequestPayload.getStatus(),
                        transactionId,
                        payUCallbackRequestPayload.getExternalTransactionId(),
                        errorCode,
                        errorMessage,
                        uid);
            }

            final URIBuilder returnUrl = new URIBuilder(statusWebUrl);
            returnUrl.addParameter(TRANSACTION_ID, transactionId);
            returnUrl.addParameter(SESSION_ID, SessionContextHolder.get().getId().toString());
            return returnUrl.build();
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY006, e);
        } finally {
            transactionManager.upsert(transaction);
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

    private String getChecksumHashForPayment(UUID transactionId, String udf1, String email, String firstName, String planTitle, double amount) {
        String rawChecksum = payUMerchantKey
                + PIPE_SEPARATOR + transactionId.toString() + PIPE_SEPARATOR + amount + PIPE_SEPARATOR + planTitle
                + PIPE_SEPARATOR + firstName + PIPE_SEPARATOR + email + PIPE_SEPARATOR + udf1 + "||||||||||" + payUSalt;
        return EncryptionUtils.generateSHA512Hash(rawChecksum);
    }

    private boolean validateCallbackChecksum(String transactionId, String transactionStatus, String udf1, String email, String firstName, String planTitle, double amount, String payUResponseHash) {
        String generatedString =
                payUSalt + PIPE_SEPARATOR + transactionStatus + "||||||||||" + udf1 + PIPE_SEPARATOR + email + PIPE_SEPARATOR
                        + firstName + PIPE_SEPARATOR + planTitle + PIPE_SEPARATOR + amount + PIPE_SEPARATOR + transactionId
                        + PIPE_SEPARATOR + payUMerchantKey;
        final String generatedHash = EncryptionUtils.generateSHA512Hash(generatedString);
        assert generatedHash != null;
        return generatedHash.equals(payUResponseHash);
    }

    private <T> T getValueFromSession(String key) {
        Session<SessionDTO> session = SessionContextHolder.get();
        return session.getBody().get(key);
    }

    private <T> void putValueInSession(String key, T value) {
        Session<SessionDTO> session = SessionContextHolder.get();
        session.getBody().put(key, value);
    }

    @Override
    public BaseResponse<String> doVerify(VerificationRequest verificationRequest) {
        VerificationType verificationType = verificationRequest.getVerificationType();
        switch (verificationType){
            case VPA:
                MultiValueMap<String, String> verifyVpaRequest = buildPayUInfoRequest(PayUCommand.VERIFY_VPA.getCode(), verificationRequest.getVerifyValue());
                PayuVpaVerificationResponse verificationResponse = getInfoFromPayU(verifyVpaRequest, PayuVpaVerificationResponse.class);
                if(verificationResponse.getIsVPAValid() == 1)
                    verificationResponse.setValid(true);
                return BaseResponse.<String>builder().body(JsonUtils.GSON.toJson(verificationResponse)).status(HttpStatus.OK).build();
            case BIN:
                MultiValueMap<String, String> verifyBinRequest = buildPayUInfoRequest(PayUCommand.CARD_BIN_INFO.getCode(), verificationRequest.getVerifyValue());
                PayUCardInfo payUCardInfo = getInfoFromPayU(verifyBinRequest, PayUCardInfo.class);
                if(payUCardInfo.getIsDomestic().equalsIgnoreCase("Y"))
                    payUCardInfo.setValid(true);
                return BaseResponse.<String>builder().body(JsonUtils.GSON.toJson(payUCardInfo)).status(HttpStatus.OK).build();
        }
       return null;
    }
}
