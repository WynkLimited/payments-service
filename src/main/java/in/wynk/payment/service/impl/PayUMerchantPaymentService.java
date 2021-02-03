package in.wynk.payment.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import in.wynk.common.constant.SessionKeys;
import in.wynk.common.dto.SessionDTO;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.BaseLoggingMarkers;
import in.wynk.payment.common.enums.BillingCycle;
import in.wynk.payment.common.utils.BillingUtils;
import in.wynk.payment.core.constant.*;
import in.wynk.payment.core.dao.entity.Card;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.entity.UserPreferredPayment;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.payu.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.dto.response.ChargingStatusResponse.ChargingStatusResponseBuilder;
import in.wynk.payment.dto.response.PayuVpaVerificationResponse;
import in.wynk.payment.dto.response.payu.PayUMandateUpiStatusResponse;
import in.wynk.payment.dto.response.payu.PayURenewalResponse;
import in.wynk.payment.dto.response.payu.PayUUserCardDetailsResponse;
import in.wynk.payment.dto.response.payu.PayUVerificationResponse;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import in.wynk.subscription.common.dto.PlanDTO;
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

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY015;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYU_API_FAILURE;
import static in.wynk.payment.dto.payu.PayUConstants.*;

@Slf4j
@Service(BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE)
public class PayUMerchantPaymentService implements IRenewalMerchantPaymentService, IMerchantVerificationService, IMerchantTransactionDetailsService, IUserPreferredPaymentService {

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

    private final Gson gson;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimiter rateLimiter = RateLimiter.create(6.0);
    private final IMerchantTransactionService merchantTransactionService;

    public PayUMerchantPaymentService(Gson gson,
                                      ObjectMapper objectMapper,
                                      ApplicationEventPublisher eventPublisher,
                                      PaymentCachingService paymentCachingService,
                                      IMerchantTransactionService merchantTransactionService,
                                      @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate) {
        this.gson = gson;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.eventPublisher = eventPublisher;
        this.cachingService = paymentCachingService;
        this.merchantTransactionService = merchantTransactionService;
    }

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        String returnUrl = processCallback(callbackRequest);
        return BaseResponse.redirectResponse(returnUrl);
    }

    @Override
    public BaseResponse<Map<String, String>> doCharging(ChargingRequest chargingRequest) {
        Map<String, String> payUPayload = startPaymentChargingForPayU(TransactionContext.get());
        String encryptedParams;
        try {
            encryptedParams = EncryptionUtils.encrypt(gson.toJson(payUPayload), encryptionKey);
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
    public void doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        Transaction transaction = TransactionContext.get();
        MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(paymentRenewalChargingRequest.getId());
        if (merchantTransaction == null) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw new WynkRuntimeException("No merchant transaction found for Subscription");
        }
        try {
            PayURenewalResponse payURenewalResponse = objectMapper.convertValue(merchantTransaction.getResponse(), PayURenewalResponse.class);
            PayUTransactionDetails payUTransactionDetails = payURenewalResponse.getTransactionDetails().get(paymentRenewalChargingRequest.getId());
            String mode = payUTransactionDetails.getMode();
            Boolean isUpi = StringUtils.isNotEmpty(mode) && mode.equals("UPI");
            // TODO:: Remove it once migration is completed
            String transactionId = StringUtils.isNotEmpty(payUTransactionDetails.getMigratedTransactionId()) ? payUTransactionDetails.getMigratedTransactionId() : paymentRenewalChargingRequest.getId();
            if (!isUpi || validateStatusForRenewal(merchantTransaction.getExternalTransactionId(), transactionId)) {
                payURenewalResponse = doChargingForRenewal(paymentRenewalChargingRequest, merchantTransaction.getExternalTransactionId());
                payUTransactionDetails = payURenewalResponse.getTransactionDetails().get(transaction.getIdStr());
                int retryInterval = cachingService.getPlan(transaction.getPlanId()).getPeriod().getRetryInterval();
                if (payURenewalResponse.getStatus() == 1) {
                    if (PaymentConstants.SUCCESS.equalsIgnoreCase(payUTransactionDetails.getStatus())) {
                        transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                    } else if (FAILURE.equalsIgnoreCase(payUTransactionDetails.getStatus()) || PAYU_STATUS_NOT_FOUND.equalsIgnoreCase(payUTransactionDetails.getStatus())) {
                        transaction.setStatus(TransactionStatus.FAILURE.getValue());
                        if (!StringUtils.isEmpty(payUTransactionDetails.getErrorCode()) || !StringUtils.isEmpty(payUTransactionDetails.getErrorMessage())) {
                            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(payUTransactionDetails.getErrorCode()).description(payUTransactionDetails.getErrorMessage()).build());
                        }
                    } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
                            StringUtils.equalsIgnoreCase(PENDING, payUTransactionDetails.getStatus())) {
                        transaction.setStatus(TransactionStatus.INPROGRESS.getValue());
                    } else if (transaction.getInitTime().getTimeInMillis() < System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
                            StringUtils.equalsIgnoreCase(PENDING, payUTransactionDetails.getStatus())) {
                        transaction.setStatus(TransactionStatus.FAILURE.getValue());
                    }
                } else {
                    transaction.setStatus(TransactionStatus.FAILURE.getValue());
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(payUTransactionDetails.getErrorCode()).description(payUTransactionDetails.getErrorMessage()).build());
                }
            }
        } catch (WynkRuntimeException e) {
            if (e.getErrorCode().equals(PaymentErrorType.PAY014))
                transaction.setStatus(TransactionStatus.TIMEDOUT.getValue());
            else if (e.getErrorCode().equals(PaymentErrorType.PAY009) || e.getErrorCode().equals(PaymentErrorType.PAY002))
                transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw e;
        }
    }

    @Override
    public BaseResponse<ChargingStatusResponse> status(ChargingStatusRequest chargingStatusRequest) {
        ChargingStatusResponse statusResponse;
        Transaction transaction = TransactionContext.get();
        switch (chargingStatusRequest.getMode()) {
            case SOURCE:
                statusResponse = fetchChargingStatusFromPayUSource(transaction);
                break;
            case LOCAL:
                statusResponse = fetchChargingStatusFromDataSource(transaction);
                break;
            default:
                throw new WynkRuntimeException(PaymentErrorType.PAY008);
        }
        return BaseResponse.<ChargingStatusResponse>builder()
                .status(HttpStatus.OK)
                .body(statusResponse)
                .build();
    }

    private ChargingStatusResponse fetchChargingStatusFromPayUSource(Transaction transaction) {
        fetchAndUpdateTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY004);
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY003);
        }
        ChargingStatusResponseBuilder responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus())
                .tid(transaction.getIdStr()).planId(transaction.getPlanId());
        if (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != PaymentEvent.POINT_PURCHASE) {
            responseBuilder.validity(cachingService.validTillDate(transaction.getPlanId()));
        }
        return responseBuilder.build();
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
            AnalyticService.update(EXTERNAL_TRANSACTION_ID, payUTransactionDetails.getPayUExternalTxnId());
            int retryInterval = cachingService.getPlan(transaction.getPlanId()).getPeriod().getRetryInterval();
            if (payUChargingVerificationResponse.getStatus() == 1) {
                if (PaymentConstants.SUCCESS.equalsIgnoreCase(payUTransactionDetails.getStatus())) {
                    finalTransactionStatus = TransactionStatus.SUCCESS;
                } else if (FAILURE.equalsIgnoreCase(payUTransactionDetails.getStatus()) || PAYU_STATUS_NOT_FOUND.equalsIgnoreCase(payUTransactionDetails.getStatus())) {
                    finalTransactionStatus = TransactionStatus.FAILURE;
                } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
                        StringUtils.equalsIgnoreCase(PENDING, payUTransactionDetails.getStatus())) {
                    finalTransactionStatus = TransactionStatus.INPROGRESS;
                } else if (transaction.getInitTime().getTimeInMillis() < System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
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
            log.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } finally {
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    private ChargingStatusResponse fetchChargingStatusFromDataSource(Transaction transaction) {
        ChargingStatusResponseBuilder responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus())
                .tid(transaction.getIdStr()).planId(transaction.getPlanId());
        if (transaction.getStatus() == TransactionStatus.SUCCESS) {
            responseBuilder.validity(cachingService.validTillDate(transaction.getPlanId()));
        }
        return responseBuilder.build();
    }

    private Map<String, String> startPaymentChargingForPayU(Transaction transaction) {
        final int planId = transaction.getPlanId();
        final PlanDTO selectedPlan = cachingService.getPlan(planId);
        double finalPlanAmount = transaction.getAmount();
        String uid = transaction.getUid();
        String msisdn = transaction.getMsisdn();
        final String email = uid + BASE_USER_EMAIL;
        Map<String, String> payload = new HashMap<>();
        String userCredentials = payUMerchantKey + COLON + uid;
        String sid = SessionContextHolder.get().getId().toString();
        Map<String, String> payloadTemp = getPayload(transaction.getId(), email, uid, planId, finalPlanAmount);
        if (transaction.getType() == PaymentEvent.SUBSCRIBE || transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
            payloadTemp = getPayload(transaction.getId(), email, uid, planId, finalPlanAmount, selectedPlan, transaction.getType());
        }
        // Mandatory according to document
        payload.putAll(payloadTemp);
        payload.put(PAYU_MERCHANT_KEY, payUMerchantKey);
        payload.put(PAYU_REQUEST_TRANSACTION_ID, transaction.getId().toString());
        payload.put(PAYU_TRANSACTION_AMOUNT, String.valueOf(finalPlanAmount));
        payload.put(PAYU_PRODUCT_INFO, String.valueOf(planId));
        payload.put(PAYU_CUSTOMER_FIRSTNAME, uid);
        payload.put(PAYU_CUSTOMER_EMAIL, email);
        payload.put(PAYU_CUSTOMER_MSISDN, msisdn);
        payload.put(PAYU_SUCCESS_URL, payUSuccessUrl + sid);
        payload.put(PAYU_FAILURE_URL, payUFailureUrl + sid);
        // Not in document
        payload.put(PAYU_IS_FALLBACK_ATTEMPT, String.valueOf(false));
        payload.put(ERROR, PAYU_REDIRECT_MESSAGE);
        payload.put(PAYU_USER_CREDENTIALS, userCredentials);
        putValueInSession(SessionKeys.TRANSACTION_ID, transaction.getId().toString());
        putValueInSession(SessionKeys.PAYMENT_CODE, PaymentCode.PAYU.getCode());
        return payload;
    }

    private Map<String, String> getPayload(UUID transactionId, String email, String uid, int planId, double finalPlanAmount) {
        Map<String, String> payload = new HashMap<>();
        String udf1 = StringUtils.EMPTY;
        String reqType = PaymentRequestType.DEFAULT.name();
        String checksumHash = getChecksumHashForPayment(transactionId, udf1, email, uid, String.valueOf(planId), finalPlanAmount);
        payload.put(PAYU_HASH, checksumHash);
        payload.put(PAYU_REQUEST_TYPE, reqType);
        payload.put(PAYU_UDF1_PARAMETER, udf1);
        return payload;
    }

    private Map<String, String> getPayload(UUID transactionId, String email, String uid, int planId, double finalPlanAmount, PlanDTO selectedPlan, PaymentEvent paymentEvent) {
        Map<String, String> payload = new HashMap<>();
        String reqType = PaymentRequestType.SUBSCRIBE.name();
        String udf1 = PAYU_SI_KEY.toUpperCase();
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.HOUR, 24);
        Date today = cal.getTime();
        cal.add(Calendar.YEAR, 5); // 5 yrs from now
        Date next5Year = cal.getTime();
        Boolean isFreeTrial = paymentEvent == PaymentEvent.TRIAL_SUBSCRIPTION;
        Integer validTillDays = Math.toIntExact(selectedPlan.getPeriod().getTimeUnit().toDays(selectedPlan.getPeriod().getValidity()));
        Integer freeTrialValidity = validTillDays;
        BillingUtils billingUtils;
        if (isFreeTrial) {
            freeTrialValidity = cachingService.getPlan(selectedPlan.getLinkedFreePlanId()).getPeriod().getValidity();
        }
        if (freeTrialValidity == validTillDays) {
            billingUtils = new BillingUtils(validTillDays);
        } else {
            billingUtils = new BillingUtils(1, BillingCycle.ADHOC);
        }
        try {
            String siDetails = objectMapper.writeValueAsString(new SiDetails(billingUtils.getBillingCycle(), billingUtils.getBillingInterval(), selectedPlan.getFinalPrice(), today, next5Year));
            String checksumHash = getChecksumHashForPayment(transactionId, udf1, email, uid, String.valueOf(planId), finalPlanAmount, siDetails);
            payload.put(PAYU_SI_KEY, "1");
            payload.put(PAYU_API_VERSION, "7");
            payload.put(PAYU_HASH, checksumHash);
            payload.put(PAYU_UDF1_PARAMETER, udf1);
            payload.put(PAYU_SI_DETAILS, siDetails);
            payload.put(PAYU_REQUEST_TYPE, reqType);
            payload.put(PAYU_FREE_TRIAL, isFreeTrial?"1":"0");
        } catch (Exception e) {
            log.error("Error Creating SiDetails Object");
        }
        return payload;
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

    private boolean validateStatusForRenewal(String mihpayid, String transactionId) {
        LinkedHashMap<String, Object> orderedMap = new LinkedHashMap<>();
        orderedMap.put(PAYU_RESPONSE_AUTH_PAYUID, mihpayid);
        orderedMap.put(PAYU_REQUEST_ID, transactionId);
        String variable = gson.toJson(orderedMap);
        String hash = generateHashForPayUApi(PayUCommand.UPI_MANDATE_STATUS.getCode(), variable);
        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
        requestMap.add(PAYU_COMMAND, PayUCommand.UPI_MANDATE_STATUS.getCode());
        requestMap.add(PAYU_VARIABLE1, variable);
        requestMap.add(PAYU_HASH, hash);
        String response = null;
        rateLimiter.acquire();
        try {
            response = restTemplate.postForObject(payUInfoApiUrl, requestMap, String.class);
        } catch (RestClientException e) {
            if (e.getRootCause() != null) {
                if (e.getRootCause() instanceof SocketTimeoutException || e.getRootCause() instanceof ConnectTimeoutException) {
                    log.error(
                            PaymentLoggingMarker.PAYU_RENEWAL_STATUS_ERROR,
                            "Socket timeout but valid for reconciliation for request : {} due to {}",
                            requestMap,
                            e.getMessage(),
                            e);
                    throw new WynkRuntimeException(PaymentErrorType.PAY014);
                } else {
                    throw new WynkRuntimeException(PaymentErrorType.PAY009, e);
                }
            } else {
                throw new WynkRuntimeException(PaymentErrorType.PAY009, e);
            }
        } catch (Exception ex){
            log.error(PAYU_API_FAILURE, ex.getMessage(), ex);
            throw new WynkRuntimeException(PAY015, ex);
        }
        PayUMandateUpiStatusResponse paymentResponse = gson.fromJson(response, PayUMandateUpiStatusResponse.class);
        if (paymentResponse != null && paymentResponse.getStatus().equals("active")) {
            return true;
        }
        return false;
    }

    private PayURenewalResponse doChargingForRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest, String mihpayid) {
        Transaction transaction = TransactionContext.get();
        Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        LinkedHashMap<String, Object> orderedMap = new LinkedHashMap<>();
        String uid = paymentRenewalChargingRequest.getUid();
        String msisdn = paymentRenewalChargingRequest.getMsisdn();
        double amount = cachingService.getPlan(transaction.getPlanId()).getFinalPrice();
        final String email = uid + BASE_USER_EMAIL;
        orderedMap.put(PAYU_RESPONSE_AUTH_PAYUID_SMALL, mihpayid);
        orderedMap.put(PAYU_TRANSACTION_AMOUNT, amount);
        orderedMap.put(PAYU_REQUEST_TRANSACTION_ID, transaction.getIdStr());
        orderedMap.put(PAYU_CUSTOMER_MSISDN, msisdn);
        orderedMap.put(PAYU_CUSTOMER_EMAIL, email);
        String variable = gson.toJson(orderedMap);
        String hash = generateHashForPayUApi(PayUCommand.SI_TRANSACTION.getCode(), variable);
        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
        requestMap.add(PAYU_COMMAND, PayUCommand.SI_TRANSACTION.getCode());
        requestMap.add(PAYU_HASH, hash);
        requestMap.add(PAYU_VARIABLE1, variable);
        String response = null;
        rateLimiter.acquire();
        try {
            merchantTransactionEventBuilder.request(requestMap);
            response = restTemplate.postForObject(payUInfoApiUrl, requestMap, String.class);
            PayURenewalResponse paymentResponse = gson.fromJson(response, PayURenewalResponse.class);
            merchantTransactionEventBuilder.response(paymentResponse);
            if (paymentResponse == null) {
                paymentResponse = new PayURenewalResponse();
            } else {
                merchantTransactionEventBuilder.externalTransactionId(paymentResponse.getTransactionDetails().get(transaction.getIdStr()).getPayUExternalTxnId());
            }
            return paymentResponse;
        } catch (RestClientException e) {
            PaymentErrorEvent.Builder errorEventBuilder = PaymentErrorEvent.builder(transaction.getIdStr());
            if (e.getRootCause() != null) {
                if (e.getRootCause() instanceof SocketTimeoutException || e.getRootCause() instanceof ConnectTimeoutException) {
                    log.error(
                            PaymentLoggingMarker.PAYU_RENEWAL_STATUS_ERROR,
                            "Socket timeout but valid for reconciliation for request : {} due to {}",
                            requestMap,
                            e.getMessage(),
                            e);
                    errorEventBuilder.code(PaymentErrorType.PAY014.getErrorCode());
                    errorEventBuilder.description(PaymentErrorType.PAY014.getErrorMessage());
                    eventPublisher.publishEvent(errorEventBuilder.build());
                    throw new WynkRuntimeException(PaymentErrorType.PAY014);
                } else {
                    errorEventBuilder.code(PaymentErrorType.PAY009.getErrorCode());
                    errorEventBuilder.description(PaymentErrorType.PAY009.getErrorMessage());
                    eventPublisher.publishEvent(errorEventBuilder.build());
                    throw new WynkRuntimeException(PaymentErrorType.PAY009, e);
                }
            } else {
                errorEventBuilder.code(PaymentErrorType.PAY009.getErrorCode());
                errorEventBuilder.description(PaymentErrorType.PAY009.getErrorMessage());
                eventPublisher.publishEvent(errorEventBuilder.build());
                throw new WynkRuntimeException(PaymentErrorType.PAY009, e);
            }
        } finally {
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    private <T> T getInfoFromPayU(MultiValueMap<String, String> request, Class<T> target) {
        String response;
        try {
        response = restTemplate.postForObject(payUInfoApiUrl, request, String.class);
        } catch (HttpStatusCodeException ex) {
            log.error(PAYU_API_FAILURE, ex.getResponseBodyAsString(), ex);
            throw new WynkRuntimeException(PAY015, ex);
        } catch (Exception ex) {
            log.error(PAYU_API_FAILURE, ex.getMessage(), ex);
            throw new WynkRuntimeException(PAY015, ex);
        }
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

    private String processCallback(CallbackRequest callbackRequest) {
        final Transaction transaction = TransactionContext.get();
        final String transactionId = transaction.getIdStr();
        try {
            SessionDTO sessionDTO = SessionContextHolder.getBody();
            final PayUCallbackRequestPayload payUCallbackRequestPayload = gson.fromJson(gson.toJsonTree(callbackRequest.getBody()), PayUCallbackRequestPayload.class);

            final String errorCode = payUCallbackRequestPayload.getError();
            final String errorMessage = payUCallbackRequestPayload.getErrorMessage();

            final boolean isValidHash = validateCallbackChecksum(transactionId,
                    payUCallbackRequestPayload.getStatus(),
                    payUCallbackRequestPayload.getUdf1(),
                    payUCallbackRequestPayload.getEmail(),
                    payUCallbackRequestPayload.getFirstName(),
                    String.valueOf(transaction.getPlanId()),
                    transaction.getAmount(),
                    payUCallbackRequestPayload.getResponseHash());

            if (isValidHash) {
                fetchAndUpdateTransactionFromSource(transaction);
//                transactionManager.updateAndPublishSync(transaction, this::fetchAndUpdateTransactionFromSource);

                if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                    log.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY300);
                } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                    log.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY301);
                } else if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                    String successUrl = sessionDTO.get(SUCCESS_WEB_URL);
                    if (StringUtils.isEmpty(successUrl)) {
                        successUrl = new StringBuilder(SUCCESS_PAGE).append(SessionContextHolder.getId())
                                .append(SLASH)
                                .append(sessionDTO.<String>get(OS))
                                .append(QUESTION_MARK)
                                .append(SERVICE)
                                .append(EQUAL)
                                .append(sessionDTO.<String>get(SERVICE))
                                .append(AND)
                                .append(BUILD_NO)
                                .append(EQUAL)
                                .append(sessionDTO.<Integer>get(BUILD_NO))
                                .toString();
                    }
                    return successUrl;
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

    private String getChecksumHashForPayment(UUID transactionId, String udf1, String email, String firstName, String planTitle, double amount, String siDetails) {
        String rawChecksum = payUMerchantKey + PIPE_SEPARATOR + transactionId.toString() + PIPE_SEPARATOR + amount + PIPE_SEPARATOR + planTitle +
                PIPE_SEPARATOR + firstName + PIPE_SEPARATOR + email + PIPE_SEPARATOR + udf1 + "||||||||||" + siDetails + PIPE_SEPARATOR + payUSalt;
        return EncryptionUtils.generateSHA512Hash(rawChecksum);
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
    public BaseResponse<?> doVerify(VerificationRequest verificationRequest) {
        VerificationType verificationType = verificationRequest.getVerificationType();
        try {
            switch (verificationType) {
                case VPA:
                    MultiValueMap < String, String > verifyVpaRequest = buildPayUInfoRequest(PayUCommand.VERIFY_VPA.getCode(), verificationRequest.getVerifyValue());
                    PayuVpaVerificationResponse verificationResponse = getInfoFromPayU(verifyVpaRequest, PayuVpaVerificationResponse.class);
                    if (verificationResponse.getIsVPAValid() == 1)
                        verificationResponse.setValid(true);
                    return BaseResponse. < PayuVpaVerificationResponse > builder().body(verificationResponse).status(HttpStatus.OK).build();
                case BIN:
                    MultiValueMap < String, String > verifyBinRequest = buildPayUInfoRequest(PayUCommand.CARD_BIN_INFO.getCode(), verificationRequest.getVerifyValue());
                    PayUCardInfo payUCardInfo = getInfoFromPayU(verifyBinRequest, PayUCardInfo.class);
                    if (payUCardInfo.getIsDomestic().equalsIgnoreCase("Y"))
                        payUCardInfo.setValid(true);
                    return BaseResponse. < PayUCardInfo > builder().body(payUCardInfo).status(HttpStatus.OK).build();
            }
        } catch (Exception ex) {
            throw ex;
        }
        return BaseResponse.status(false);
    }

    @Override
    public MerchantTransaction getMerchantTransactionDetails(Map<String, String> params) {
        MerchantTransaction.MerchantTransactionBuilder builder = MerchantTransaction.builder().id(params.get(TXN_ID));
        final String tid = params.containsKey(MIGRATED) && Boolean.valueOf(params.get(MIGRATED)) ? params.get(MIGRATED_TXN_ID) : params.get(TXN_ID);
        try {
            MultiValueMap<String, String> payUChargingVerificationRequest = this.buildPayUInfoRequest(PayUCommand.VERIFY_PAYMENT.getCode(), tid);
            PayUVerificationResponse payUChargingVerificationResponse = this.getInfoFromPayU(payUChargingVerificationRequest, PayUVerificationResponse.class);
            builder.request(payUChargingVerificationRequest);
            builder.response(payUChargingVerificationResponse);
            PayUTransactionDetails payUTransactionDetails = payUChargingVerificationResponse.getTransactionDetails().get(tid);
            payUTransactionDetails.setMigratedTransactionId(tid);
            if (params.containsKey(MIGRATED) && Boolean.valueOf(params.get(MIGRATED))) {
                payUChargingVerificationResponse.getTransactionDetails().remove(tid);
                payUChargingVerificationResponse.getTransactionDetails().put(params.get(TXN_ID), payUTransactionDetails);
            }
            builder.externalTransactionId(payUTransactionDetails.getPayUExternalTxnId());
        } catch (HttpStatusCodeException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        }
        return builder.build();
    }

    public UserPreferredPayment getUserPreferredPayments(String uid) {
        String userCredentials = payUMerchantKey + COLON + uid;
        MultiValueMap<String, String> userCardDetailsRequest = buildPayUInfoRequest(PayUCommand.USER_CARD_DETAILS.getCode(), userCredentials);
        PayUUserCardDetailsResponse userCardDetailsResponse = getInfoFromPayU(userCardDetailsRequest, PayUUserCardDetailsResponse.class);
        Card.Builder cardBuilder = new Card.Builder().paymentCode(PaymentCode.PAYU);
        for (String cardToken : userCardDetailsResponse.getUserCards().keySet()) {
            cardBuilder.cardDetails(Card.CardDetails.builder().cardToken(cardToken).build());
        }
        UserPreferredPayment userPreferredPayment = UserPreferredPayment.builder()
                .uid(uid)
                .option(cardBuilder.build())
                .build();
        return userPreferredPayment;
    }

}