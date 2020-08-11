package in.wynk.payment.service.impl;

import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import in.wynk.commons.constants.SessionKeys;
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
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.payu.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.PayuVpaVerificationResponse;
import in.wynk.payment.dto.response.payu.PayURenewalResponse;
import in.wynk.payment.dto.response.payu.PayUUserCardDetailsResponse;
import in.wynk.payment.dto.response.payu.PayUVerificationResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.IMerchantVerificationService;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ConnectTimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.commons.constants.Constants.*;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.dto.payu.PayUConstants.*;

/**
 * TODO:
 * txn decouple
 * payment error decouple
 * merchant txn decouple
 * polling queue decouple
 */
@Slf4j
@Service(BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE)
public class PayUMerchantPaymentService implements IRenewalMerchantPaymentService, IMerchantVerificationService {

    private final Gson gson;
    private final RestTemplate restTemplate;
    private final PaymentCachingService cachingService;
    private final ISQSMessagePublisher sqsMessagePublisher;
    private final ApplicationEventPublisher eventPublisher;
    private final ITransactionManagerService transactionManager;
    private final RateLimiter rateLimiter = RateLimiter.create(6.0);
    @Value("${payment.merchant.payu.salt}")
    private String payUSalt;
    @Value("${payment.encKey}")
    private String encryptionKey;
    @Value("${payment.merchant.payu.key}")
    private String payUMerchantKey;
    @Value("${payment.merchant.payu.api.info}")
    private String payUInfoApiUrl;
    @Value("${payment.success.page}")
    private String SUCCESS_PAGE;
    @Value("${payment.merchant.payu.internal.callback.successUrl}")
    private String payUSuccessUrl;
    @Value("${payment.merchant.payu.internal.callback.failureUrl}")
    private String payUFailureUrl;
    @Value("${payment.pooling.queue.reconciliation.name}")
    private String reconciliationQueue;
    @Value("${payment.pooling.queue.reconciliation.sqs.producer.delayInSecond}")
    private int reconciliationMessageDelay;

    public PayUMerchantPaymentService(Gson gson,
                                      RestTemplate restTemplate,
                                      PaymentCachingService paymentCachingService,
                                      ITransactionManagerService transactionManager,
                                      @Qualifier(in.wynk.queue.constant.BeanConstant.SQS_EVENT_PRODUCER) ISQSMessagePublisher sqsMessagePublisher, ApplicationEventPublisher eventPublisher) {
        this.gson = gson;
        this.restTemplate = restTemplate;
        this.eventPublisher = eventPublisher;
        this.cachingService = paymentCachingService;
        this.transactionManager = transactionManager;
        this.sqsMessagePublisher = sqsMessagePublisher;
    }

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest, Transaction transaction) {
        String returnUrl = processCallback(callbackRequest, transaction);
        return BaseResponse.redirectResponse(returnUrl);
    }

    @Override
    public BaseResponse<Map<String, String>> doCharging(ChargingRequest chargingRequest, Transaction transaction) {
        Map<String, String> payUpayload = startPaymentChargingForPayU(transaction);
        String encryptedParams;
        try {
            encryptedParams = EncryptionUtils.encrypt(gson.toJson(payUpayload), encryptionKey);
        } catch (Exception e) {
            log.error(BaseLoggingMarkers.ENCRYPTION_ERROR, e.getMessage(), e);
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
    public BaseResponse<Void> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
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
            log.error("Exception while parsing acknowledgement response.", throwable);
        } finally {
            //      TODO: Create subscription renewal charging response and return it.
        }
        return null;
    }

    @Override
    public BaseResponse<ChargingStatusResponse> status(ChargingStatusRequest chargingStatusRequest) {
        ChargingStatusResponse statusResponse;
        switch (chargingStatusRequest.getMode()) {
            case SOURCE:
                statusResponse = fetchChargingStatusFromPayUSource(chargingStatusRequest);
                break;
            case LOCAL:
                statusResponse = fetchChargingStatusFromDataSource(chargingStatusRequest);
                break;
            default:
                throw new WynkRuntimeException(PaymentErrorType.PAY008);
        }
        return BaseResponse.<ChargingStatusResponse>builder()
                .status(HttpStatus.OK)
                .body(statusResponse)
                .build();
    }


    private ChargingStatusResponse fetchChargingStatusFromPayUSource(ChargingStatusRequest request) {
        final Transaction transaction = transactionManager.get(request.getTransactionId());
        transactionManager.updateAndPublishAsync(transaction, this::fetchAndUpdateTransactionFromSource);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY004);
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY003);
        }
        return ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).build();
    }

    public void fetchAndUpdateTransactionFromSource(Transaction transaction) {
        Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            TransactionStatus finalTransactionStatus = TransactionStatus.UNKNOWN;
            MultiValueMap<String, String> payUChargingVerificationRequest = this.buildPayUInfoRequest(PayUCommand.VERIFY_PAYMENT.getCode(), transaction.getId().toString());
            merchantTransactionEventBuilder.request(payUChargingVerificationRequest);
            PayUVerificationResponse payUChargingVerificationResponse = this.getInfoFromPayU(payUChargingVerificationRequest, PayUVerificationResponse.class);
            merchantTransactionEventBuilder.response(payUChargingVerificationResponse);
            PayUTransactionDetails payUTransactionDetails = payUChargingVerificationResponse.getTransactionDetails().get(transaction.getId().toString());
            merchantTransactionEventBuilder.externalTransactionId(payUTransactionDetails.getPayUExternalTxnId());
            if (payUChargingVerificationResponse.getStatus() == 1) {
                if (SUCCESS.equalsIgnoreCase(payUTransactionDetails.getStatus())) {
                    finalTransactionStatus = TransactionStatus.SUCCESS;
                } else if (FAILURE.equalsIgnoreCase(payUTransactionDetails.getStatus()) || PAYU_STATUS_NOT_FOUND.equalsIgnoreCase(payUTransactionDetails.getStatus())) {
                    finalTransactionStatus = TransactionStatus.FAILURE;
                } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3 &&
                        StringUtils.equalsIgnoreCase(PENDING, payUTransactionDetails.getStatus())) {
                    finalTransactionStatus = TransactionStatus.INPROGRESS;
                } else if (transaction.getInitTime().getTimeInMillis() < System.currentTimeMillis() - ONE_DAY_IN_MILLI * 3 &&
                        StringUtils.equalsIgnoreCase(PENDING, payUTransactionDetails.getStatus())) {
                    finalTransactionStatus = TransactionStatus.FAILURE;
                }
            } else {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }

            if (finalTransactionStatus == TransactionStatus.FAILURE) {
                if (!StringUtils.isEmpty(payUTransactionDetails.getErrorCode()) || !StringUtils.isEmpty(payUTransactionDetails.getErrorMessage())) {
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(payUTransactionDetails.getErrorCode()).description(payUTransactionDetails.getErrorMessage()).build());
                }
            }

            transaction.setStatus(finalTransactionStatus.name());
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ",e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } finally {
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }


    private ChargingStatusResponse fetchChargingStatusFromDataSource(ChargingStatusRequest chargingStatusRequest) {
        Transaction transaction = transactionManager.get(chargingStatusRequest.getTransactionId());
        return ChargingStatusResponse.builder()
                .transactionStatus(transaction.getStatus())
                .build();
    }

    private Map<String, String> startPaymentChargingForPayU(Transaction transaction) {
        String udf1 = StringUtils.EMPTY;
        String reqType = PaymentRequestType.DEFAULT.name();
//
//        final SessionDTO sessionDTO = SessionContextHolder.getBody();
        final int planId = transaction.getPlanId();
//        final String uid = sessionDTO.get(SessionKeys.UID);
//        final String msisdn = Utils.getTenDigitMsisdn(sessionDTO.get(SessionKeys.MSISDN));
        final PlanDTO selectedPlan = cachingService.getPlan(planId);
        final double finalPlanAmount = selectedPlan.getFinalPrice();
//
//        final TransactionEvent eventType = chargingRequest.isAutoRenew() ? TransactionEvent.SUBSCRIBE : TransactionEvent.PURCHASE;

//        final Transaction transaction = transactionManager.initiateTransaction(uid, msisdn, chargingRequest.getPlanId(), finalPlanAmount, PaymentCode.PAYU, eventType);

        String uid = transaction.getUid();
        String msisdn = transaction.getMsisdn();
        final String email = uid + BASE_USER_EMAIL;
        Map<String, String> paylaod = new HashMap<>();
        if (!PlanType.ONE_TIME_SUBSCRIPTION.equals(selectedPlan.getPlanType())) {
            reqType = PaymentRequestType.SUBSCRIBE.name();
            udf1 = PAYU_SI_KEY.toUpperCase();
            paylaod.put(PAYU_SI_KEY, "1");
        }

        String checksumHash = getChecksumHashForPayment(transaction.getId(), udf1, email, uid, String.valueOf(planId), finalPlanAmount);
        String userCredentials = payUMerchantKey + COLON + uid;
        String sid = SessionContextHolder.get().getId().toString();
        paylaod.put(PAYU_REQUEST_TYPE, reqType);
        paylaod.put(PAYU_MERCHANT_KEY, payUMerchantKey);
        paylaod.put(PAYU_REQUEST_TRANSACTION_ID, transaction.getId().toString());
        paylaod.put(PAYU_TRANSACTION_AMOUNT, String.valueOf(finalPlanAmount));
        paylaod.put(PAYU_PRODUCT_INFO, String.valueOf(planId));
        paylaod.put(PAYU_CUSTOMER_FIRSTNAME, uid);
        paylaod.put(PAYU_CUSTOMER_EMAIL, email);
        paylaod.put(PAYU_CUSTOMER_MSISDN, msisdn);
        paylaod.put(PAYU_HASH, checksumHash);
        paylaod.put(PAYU_SUCCESS_URL, payUSuccessUrl + sid);
        paylaod.put(PAYU_FAILURE_URL, payUFailureUrl + sid);
//      paylaod.put(PAYU_ENFORCE_PAY_METHOD, chargingRequest.getEnforcePayment()) upto FE since it knows which option is chosen.
        paylaod.put(PAYU_UDF1_PARAMETER, udf1);
        paylaod.put(PAYU_IS_FALLBACK_ATTEMPT, String.valueOf(false));
        paylaod.put(ERROR, PAYU_REDIRECT_MESSAGE);
        paylaod.put(PAYU_USER_CREDENTIALS, userCredentials);
        putValueInSession(SessionKeys.WYNK_TRANSACTION_ID, transaction.getId().toString());
        putValueInSession(SessionKeys.PAYMENT_CODE, PaymentCode.PAYU.getCode());
//
//        PaymentReconciliationMessage reconciliationMessage = new PaymentReconciliationMessage(transaction);
//        publishSQSMessage(reconciliationQueue, reconciliationMessageDelay, reconciliationMessage);

        return paylaod;
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
                    log.error(
                            PaymentLoggingMarker.PAYU_RENEWAL_STATUS_ERROR,
                            "Socket timeout but valid for reconciliation for request : {} due to {}",
                            requestMap,
                            e.getMessage(),
                            e);
                } else if (e.getRootCause() instanceof ConnectTimeoutException) {
                    timeOut = true;
                    log.error(
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
        return gson.fromJson(response, target);
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

    private String processCallback(CallbackRequest callbackRequest, Transaction transaction) {
        final String transactionId = transaction.getIdStr();
//        final Transaction transaction = transactionManager.get(transactionId);
        try {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            final PlanDTO selectedPlan = cachingService.getPlan(transaction.getPlanId());
            final PayUCallbackRequestPayload payUCallbackRequestPayload = gson.fromJson(gson.toJsonTree(callbackRequest.getBody()), PayUCallbackRequestPayload.class);

            final String errorCode = payUCallbackRequestPayload.getError();
            final String errorMessage = payUCallbackRequestPayload.getErrorMessage();

            final boolean isValidHash = validateCallbackChecksum(transactionId,
                    payUCallbackRequestPayload.getStatus(),
                    payUCallbackRequestPayload.getUdf1(),
                    payUCallbackRequestPayload.getEmail(),
                    payUCallbackRequestPayload.getFirstName(),
                    String.valueOf(transaction.getPlanId()),
                    selectedPlan.getFinalPrice(),
                    payUCallbackRequestPayload.getResponseHash());

            if (isValidHash) {
                transactionManager.updateAndPublishSync(transaction, this::fetchAndUpdateTransactionFromSource);

                if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                    log.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY300);
                } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                    log.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY301);
                } else if (transaction.getStatus().equals(TransactionStatus.SUCCESS)) {
                    return SUCCESS_PAGE + SessionContextHolder.getId() + SLASH + sessionDTO.get(OS);
                } else {
                    throw new PaymentRuntimeException(PaymentErrorType.PAY302);
                }
            } else {
                log.error(PaymentLoggingMarker.PAYU_CHARGING_CALLBACK_FAILURE,
                        "Invalid checksum found with transactionStatus: {}, Wynk transactionId: {}, PayU transactionId: {}, Reason: error code: {}, error message: {} for uid: {}",
                        payUCallbackRequestPayload.getStatus(),
                        transactionId,
                        payUCallbackRequestPayload.getExternalTransactionId(),
                        errorCode,
                        errorMessage,
                        transaction.getUid());
                throw new PaymentRuntimeException(PaymentErrorType.PAY302, "Invalid checksum found with transaction id:" + transactionId);
            }
        } catch (PaymentRuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
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
        DecimalFormat df = new DecimalFormat("#.00");
        String generatedString =
                payUSalt + PIPE_SEPARATOR + transactionStatus + "||||||||||" + udf1 + PIPE_SEPARATOR + email + PIPE_SEPARATOR
                        + firstName + PIPE_SEPARATOR + planTitle + PIPE_SEPARATOR + df.format(amount) + PIPE_SEPARATOR + transactionId
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
        switch (verificationType) {
            case VPA:
                MultiValueMap<String, String> verifyVpaRequest = buildPayUInfoRequest(PayUCommand.VERIFY_VPA.getCode(), verificationRequest.getVerifyValue());
                PayuVpaVerificationResponse verificationResponse = getInfoFromPayU(verifyVpaRequest, PayuVpaVerificationResponse.class);
                if (verificationResponse.getIsVPAValid() == 1)
                    verificationResponse.setValid(true);
                return BaseResponse.<String>builder().body(gson.toJson(verificationResponse)).status(HttpStatus.OK).build();
            case BIN:
                MultiValueMap<String, String> verifyBinRequest = buildPayUInfoRequest(PayUCommand.CARD_BIN_INFO.getCode(), verificationRequest.getVerifyValue());
                PayUCardInfo payUCardInfo = getInfoFromPayU(verifyBinRequest, PayUCardInfo.class);
                if (payUCardInfo.getIsDomestic().equalsIgnoreCase("Y"))
                    payUCardInfo.setValid(true);
                return BaseResponse.<String>builder().body(gson.toJson(payUCardInfo)).status(HttpStatus.OK).build();
        }
        return null;
    }
}
