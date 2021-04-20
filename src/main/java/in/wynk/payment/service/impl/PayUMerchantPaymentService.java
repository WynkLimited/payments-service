package in.wynk.payment.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
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
import in.wynk.payment.dto.response.PayUVpaVerificationResponse;
import in.wynk.payment.dto.response.payu.*;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.*;
import in.wynk.session.context.SessionContextHolder;
import in.wynk.session.dto.Session;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.PlanPeriodDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ConnectTimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY015;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY889;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYU_API_FAILURE;
import static in.wynk.payment.dto.payu.PayUConstants.*;

@Slf4j
@Service(BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE)
public class PayUMerchantPaymentService extends AbstractMerchantPaymentStatusService implements IRenewalMerchantPaymentService, IMerchantVerificationService, IMerchantTransactionDetailsService, IUserPreferredPaymentService, IMerchantPaymentRefundService {

    private final Gson gson;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimiter rateLimiter = RateLimiter.create(6.0);
    private final IMerchantTransactionService merchantTransactionService;
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

    public PayUMerchantPaymentService(Gson gson,
                                      ObjectMapper objectMapper,
                                      ApplicationEventPublisher eventPublisher,
                                      PaymentCachingService cachingService,
                                      IMerchantTransactionService merchantTransactionService,
                                      @Qualifier(BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate) {
        super(cachingService);
        this.gson = gson;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.eventPublisher = eventPublisher;
        this.cachingService = cachingService;
        this.merchantTransactionService = merchantTransactionService;
    }

    @Override
    public BaseResponse<Void> handleCallback(CallbackRequest callbackRequest) {
        String returnUrl = processCallback(callbackRequest);
        return BaseResponse.redirectResponse(returnUrl);
    }

    @Override
    public BaseResponse<Map<String, String>> doCharging(ChargingRequest chargingRequest) {
        Map<String, String> payUPayload = getPayload(TransactionContext.get());
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
        PlanPeriodDTO planPeriodDTO = cachingService.getPlan(transaction.getPlanId()).getPeriod();
        if (planPeriodDTO.getMaxRetryCount() < paymentRenewalChargingRequest.getAttemptSequence()) {
            transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw new WynkRuntimeException("Need to break the chain in Payment Renewal as maximum attempts are already exceeded");
        }
        try {
            PayURenewalResponse payURenewalResponse = objectMapper.convertValue(merchantTransaction.getResponse(), PayURenewalResponse.class);
            PayUChargingTransactionDetails payUChargingTransactionDetails = payURenewalResponse.getTransactionDetails().get(paymentRenewalChargingRequest.getId());
            String mode = payUChargingTransactionDetails.getMode();
            boolean isUpi = StringUtils.isNotEmpty(mode) && mode.equals("UPI");
            // TODO:: Remove it once migration is completed
            String transactionId = StringUtils.isNotEmpty(payUChargingTransactionDetails.getMigratedTransactionId()) ? payUChargingTransactionDetails.getMigratedTransactionId() : paymentRenewalChargingRequest.getId();
            if (!isUpi || validateStatusForRenewal(merchantTransaction.getExternalTransactionId(), transactionId)) {
                payURenewalResponse = doChargingForRenewal(paymentRenewalChargingRequest, merchantTransaction.getExternalTransactionId());
                payUChargingTransactionDetails = payURenewalResponse.getTransactionDetails().get(transaction.getIdStr());
                int retryInterval = planPeriodDTO.getRetryInterval();
                if (payURenewalResponse.getStatus() == 1) {
                    if (PaymentConstants.SUCCESS.equalsIgnoreCase(payUChargingTransactionDetails.getStatus())) {
                        transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                    } else if (FAILURE.equalsIgnoreCase(payUChargingTransactionDetails.getStatus()) || (FAILED.equalsIgnoreCase(payUChargingTransactionDetails.getStatus())) || PAYU_STATUS_NOT_FOUND.equalsIgnoreCase(payUChargingTransactionDetails.getStatus())) {
                        transaction.setStatus(TransactionStatus.FAILURE.getValue());
                        if (!StringUtils.isEmpty(payUChargingTransactionDetails.getErrorCode()) || !StringUtils.isEmpty(payUChargingTransactionDetails.getErrorMessage())) {
                            eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(payUChargingTransactionDetails.getErrorCode()).description(payUChargingTransactionDetails.getErrorMessage()).build());
                        }
                    } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
                            StringUtils.equalsIgnoreCase(PENDING, payUChargingTransactionDetails.getStatus())) {
                        transaction.setStatus(TransactionStatus.INPROGRESS.getValue());
                    } else if (transaction.getInitTime().getTimeInMillis() < System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
                            StringUtils.equalsIgnoreCase(PENDING, payUChargingTransactionDetails.getStatus())) {
                        transaction.setStatus(TransactionStatus.FAILURE.getValue());
                    }
                } else {
                    transaction.setStatus(TransactionStatus.FAILURE.getValue());
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(payUChargingTransactionDetails.getErrorCode()).description(payUChargingTransactionDetails.getErrorMessage()).build());
                }
            }
        } catch (WynkRuntimeException e) {
            if (e.getErrorCode().equals(PaymentErrorType.PAY014.getErrorCode()))
                transaction.setStatus(TransactionStatus.TIMEDOUT.getValue());
            else if (e.getErrorCode().equals(PaymentErrorType.PAY009.getErrorCode()) || e.getErrorCode().equals(PaymentErrorType.PAY002.getErrorCode()))
                transaction.setStatus(TransactionStatus.FAILURE.getValue());
            throw e;
        }
    }

    @Override
    public BaseResponse<ChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        ChargingStatusResponse statusResponse = fetchAndUpdateTransactionFromSource(transactionStatusRequest, transactionStatusRequest.getExtTxnId());
        return BaseResponse.<ChargingStatusResponse>builder().status(HttpStatus.OK).body(statusResponse).build();
    }

    private ChargingStatusResponse fetchAndUpdateTransactionFromSource(AbstractTransactionStatusRequest
                                                                               transactionStatusRequest, String extTxnId) {
        Transaction transaction = TransactionContext.get();
        if (transactionStatusRequest instanceof ChargingTransactionReconciliationStatusRequest) {
            return fetchChargingStatusFromPayUSource(transaction);
        } else if (transactionStatusRequest instanceof RefundTransactionReconciliationStatusRequest) {
            return fetchRefundStatusFromPayUSource(transaction, extTxnId);
        } else {
            throw new WynkRuntimeException(PAY889, "Unknown transaction status request to process for uid: " + transaction.getUid());
        }
    }

    private ChargingStatusResponse fetchRefundStatusFromPayUSource(Transaction transaction, String extTxnId) {
        syncRefundTransactionFromSource(transaction, extTxnId);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.error(PaymentLoggingMarker.PAYU_REFUND_STATUS_VERIFICATION, "Refund Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY004);
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(PaymentLoggingMarker.PAYU_REFUND_STATUS_VERIFICATION, "Unknown Refund Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY003);
        }
        ChargingStatusResponseBuilder responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus())
                .tid(transaction.getIdStr()).planId(transaction.getPlanId());
        if (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != PaymentEvent.POINT_PURCHASE) {
            responseBuilder.validity(cachingService.validTillDate(transaction.getPlanId()));
        }
        return responseBuilder.build();
    }

    private void syncRefundTransactionFromSource(Transaction transaction, String refundRequestId) {
        Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            MultiValueMap<String, String> payURefundStatusRequest = this.buildPayUInfoRequest(PayUCommand.CHECK_ACTION_STATUS.getCode(), refundRequestId);
            merchantTransactionEventBuilder.request(payURefundStatusRequest);
            PayUVerificationResponse<Map<String, PayURefundTransactionDetails>> payUPaymentRefundResponse = this.getInfoFromPayU(payURefundStatusRequest, new TypeReference<PayUVerificationResponse<Map<String, PayURefundTransactionDetails>>>() {
            });
            merchantTransactionEventBuilder.response(payUPaymentRefundResponse);
            Map<String, PayURefundTransactionDetails> payURefundTransactionDetails = payUPaymentRefundResponse.getTransactionDetails(refundRequestId);
            merchantTransactionEventBuilder.externalTransactionId(payURefundTransactionDetails.get(refundRequestId).getRequestId());
            AnalyticService.update(EXTERNAL_TRANSACTION_ID, payURefundTransactionDetails.get(refundRequestId).getRequestId());
            payURefundTransactionDetails.put(transaction.getIdStr(), payURefundTransactionDetails.get(refundRequestId));
            payURefundTransactionDetails.remove(refundRequestId);
            syncTransactionWithSourceResponse(PayUVerificationResponse.<PayURefundTransactionDetails>builder().transactionDetails(payURefundTransactionDetails).message(payUPaymentRefundResponse.getMessage()).status(payUPaymentRefundResponse.getStatus()).build());
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

    private ChargingStatusResponse fetchChargingStatusFromPayUSource(Transaction transaction) {
        syncChargingTransactionFromSource(transaction);
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

    public void syncChargingTransactionFromSource(Transaction transaction) {
        Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            MultiValueMap<String, String> payUChargingVerificationRequest = this.buildPayUInfoRequest(PayUCommand.VERIFY_PAYMENT.getCode(), transaction.getId().toString());
            merchantTransactionEventBuilder.request(payUChargingVerificationRequest);
            PayUVerificationResponse<PayUChargingTransactionDetails> payUChargingVerificationResponse = this.getInfoFromPayU(payUChargingVerificationRequest, new TypeReference<PayUVerificationResponse<PayUChargingTransactionDetails>>() {
            });
            merchantTransactionEventBuilder.response(payUChargingVerificationResponse);
            PayUChargingTransactionDetails payUChargingTransactionDetails = payUChargingVerificationResponse.getTransactionDetails(transaction.getId().toString());
            merchantTransactionEventBuilder.externalTransactionId(payUChargingTransactionDetails.getPayUExternalTxnId());
            AnalyticService.update(EXTERNAL_TRANSACTION_ID, payUChargingTransactionDetails.getPayUExternalTxnId());
            syncTransactionWithSourceResponse(payUChargingVerificationResponse);
            if (transaction.getStatus() == TransactionStatus.FAILURE) {
                if (!StringUtils.isEmpty(payUChargingTransactionDetails.getErrorCode()) || !StringUtils.isEmpty(payUChargingTransactionDetails.getErrorMessage())) {
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(payUChargingTransactionDetails.getErrorCode()).description(payUChargingTransactionDetails.getErrorMessage()).build());
                }
            }
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } finally {
            if (transaction.getType() != PaymentEvent.RENEW || transaction.getStatus() != TransactionStatus.FAILURE)
                eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    private void syncTransactionWithSourceResponse(PayUVerificationResponse<? extends AbstractPayUTransactionDetails> transactionDetailsWrapper) {
        TransactionStatus finalTransactionStatus = TransactionStatus.UNKNOWN;
        final Transaction transaction = TransactionContext.get();
        int retryInterval = cachingService.getPlan(transaction.getPlanId()).getPeriod().getRetryInterval();
        if (transactionDetailsWrapper.getStatus() == 1) {
            final AbstractPayUTransactionDetails transactionDetails = transactionDetailsWrapper.getTransactionDetails(transaction.getIdStr());
            if (PaymentConstants.SUCCESS.equalsIgnoreCase(transactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (FAILURE.equalsIgnoreCase(transactionDetails.getStatus()) || (FAILED.equalsIgnoreCase(transactionDetails.getStatus())) || PAYU_STATUS_NOT_FOUND.equalsIgnoreCase(transactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
                    (StringUtils.equalsIgnoreCase(PENDING, transactionDetails.getStatus()) || (transaction.getType() == PaymentEvent.REFUND && StringUtils.equalsIgnoreCase(QUEUED, transactionDetails.getStatus())))) {
                finalTransactionStatus = TransactionStatus.INPROGRESS;
            } else if (transaction.getInitTime().getTimeInMillis() < System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
                    StringUtils.equalsIgnoreCase(PENDING, transactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            }
        } else {
            finalTransactionStatus = TransactionStatus.FAILURE;
        }
        transaction.setStatus(finalTransactionStatus.getValue());
    }

    private Map<String, String> getPayload(Transaction transaction) {
        final int planId = transaction.getPlanId();
        final PlanDTO selectedPlan = cachingService.getPlan(planId);
        double finalPlanAmount = transaction.getAmount();
        String uid = transaction.getUid();
        String msisdn = transaction.getMsisdn();
        final String email = uid + BASE_USER_EMAIL;
        String userCredentials = payUMerchantKey + COLON + uid;
        String sid = SessionContextHolder.get().getId().toString();
        Map<String, String> payloadTemp;
        if (transaction.getType() == PaymentEvent.SUBSCRIBE || transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
           //payloadTemp = getPayload(transaction.getId(), email, uid, planId, finalPlanAmount, selectedPlan, transaction.getType());
            payloadTemp = getPayload(transaction.getId(), email, uid, planId, finalPlanAmount);
        } else {
            payloadTemp = getPayload(transaction.getId(), email, uid, planId, finalPlanAmount);
        }
        // Mandatory according to document
        Map<String, String> payload = new HashMap<>(payloadTemp);
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
        boolean isFreeTrial = paymentEvent == PaymentEvent.TRIAL_SUBSCRIPTION;
        BillingUtils billingUtils = getBillingUtils(selectedPlan, isFreeTrial);
        try {
            String siDetails = objectMapper.writeValueAsString(new SiDetails(billingUtils.getBillingCycle(), billingUtils.getBillingInterval(), selectedPlan.getFinalPrice(), today, next5Year));
            String checksumHash = getChecksumHashForPayment(transactionId, udf1, email, uid, String.valueOf(planId), finalPlanAmount, siDetails);
            payload.put(PAYU_SI_KEY, "1");
            payload.put(PAYU_API_VERSION, "7");
            payload.put(PAYU_HASH, checksumHash);
            payload.put(PAYU_UDF1_PARAMETER, udf1);
            payload.put(PAYU_SI_DETAILS, siDetails);
            payload.put(PAYU_REQUEST_TYPE, reqType);
            payload.put(PAYU_FREE_TRIAL, "0");
            payload.put(PAYU_FREE_TRIAL, isFreeTrial ? "1" : "0");
        } catch (Exception e) {
            log.error("Error Creating SiDetails Object");
        }
        return payload;
    }

    private BillingUtils getBillingUtils(PlanDTO selectedPlan, boolean isFreeTrial) {
        int validTillDays = Math.toIntExact(selectedPlan.getPeriod().getTimeUnit().toDays(selectedPlan.getPeriod().getValidity()));
        int freeTrialValidity = isFreeTrial ? cachingService.getPlan(selectedPlan.getLinkedFreePlanId()).getPeriod().getValidity() : validTillDays;
        return freeTrialValidity == validTillDays ? new BillingUtils(validTillDays) : new BillingUtils(1, BillingCycle.ADHOC);
    }

    //TODO: ( on AMAN) need to use to fetch user's saved cards.
    public List<String> getUserCards(String uid) {
        String userCredentials = payUMerchantKey + COLON + uid;
        MultiValueMap<String, String> userCardDetailsRequest = buildPayUInfoRequest(PayUCommand.USER_CARD_DETAILS.getCode(), userCredentials);
        PayUUserCardDetailsResponse userCardDetailsResponse = getInfoFromPayU(userCardDetailsRequest, new TypeReference<PayUUserCardDetailsResponse>() {
        });
        return userCardDetailsResponse.getUserCards()
                .entrySet()
                .parallelStream()
                .map(cardEntry -> {
                    CardDetails cardDetails = cardEntry.getValue();
                    PayUCardInfo payUCardInfo = getInfoFromPayU(buildPayUInfoRequest(PayUCommand.CARD_BIN_INFO.getCode(),
                            cardDetails.getCardBin()),
                            new TypeReference<PayUCardInfo>() {
                            });
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
        PayUMandateUpiStatusResponse paymentResponse;
        rateLimiter.acquire();
        final MultiValueMap<String, String> requestMap = buildPayUInfoRequest(PayUCommand.UPI_MANDATE_STATUS.getCode(), variable);
        try {
            paymentResponse = getInfoFromPayU(requestMap, new TypeReference<PayUMandateUpiStatusResponse>() {
            });
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
        } catch (Exception ex) {
            log.error(PAYU_API_FAILURE, ex.getMessage(), ex);
            throw new WynkRuntimeException(PAY015, ex);
        }
        return paymentResponse != null && paymentResponse.getStatus().equals("active");
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
        MultiValueMap<String, String> requestMap = buildPayUInfoRequest(PayUCommand.SI_TRANSACTION.getCode(), variable);
        rateLimiter.acquire();
        try {
            merchantTransactionEventBuilder.request(requestMap);
            PayURenewalResponse paymentResponse = getInfoFromPayU(requestMap, new TypeReference<PayURenewalResponse>() {
            });
            merchantTransactionEventBuilder.response(paymentResponse);
            if (paymentResponse == null) {
                paymentResponse = new PayURenewalResponse();
            } else {
                String newMihPayId = paymentResponse.getTransactionDetails().get(transaction.getIdStr()).getPayUExternalTxnId();
                merchantTransactionEventBuilder.externalTransactionId(StringUtils.isNotEmpty(newMihPayId) ? newMihPayId : mihpayid);
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

    private <T> T getInfoFromPayU(MultiValueMap<String, String> request, TypeReference<T> target) {
        try {
            final String response = restTemplate.exchange(RequestEntity.method(HttpMethod.POST, URI.create(payUInfoApiUrl)).body(request), String.class).getBody();
            return objectMapper.readValue(response, target);
        } catch (HttpStatusCodeException ex) {
            log.error(PAYU_API_FAILURE, ex.getResponseBodyAsString(), ex);
            throw new WynkRuntimeException(PAY015, ex);
        } catch (Exception ex) {
            log.error(PAYU_API_FAILURE, ex.getMessage(), ex);
            throw new WynkRuntimeException(PAY015, ex);
        }
    }

    private MultiValueMap<String, String> buildPayUInfoRequest(String command, String var1, String... vars) {
        String hash = generateHashForPayUApi(command, var1);
        MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
        requestMap.add(PAYU_MERCHANT_KEY, payUMerchantKey);
        requestMap.add(PAYU_COMMAND, command);
        requestMap.add(PAYU_HASH, hash);
        requestMap.add(PAYU_VARIABLE1, var1);
        if (!ArrayUtils.isEmpty(vars)) {
            for (int i = 0; i < vars.length; i++) {
                if (StringUtils.isNotEmpty(vars[i])) {
                    requestMap.add(PAYU_VARIABLE.concat(String.valueOf(i + 2)), vars[i]);
                }
            }
        }
        return requestMap;
    }

    private String generateHashForPayUApi(String command, String var1) {
        String builder = payUMerchantKey + PIPE_SEPARATOR +
                command +
                PIPE_SEPARATOR +
                var1 +
                PIPE_SEPARATOR +
                payUSalt;
        return EncryptionUtils.generateSHA512Hash(builder);
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
                syncChargingTransactionFromSource(transaction);
                if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                    log.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY300);
                } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                    log.error(PaymentLoggingMarker.PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY301);
                } else if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                    String successUrl = sessionDTO.get(SUCCESS_WEB_URL);
                    if (StringUtils.isEmpty(successUrl)) {
                        successUrl = SUCCESS_PAGE + SessionContextHolder.getId() +
                                SLASH +
                                sessionDTO.<String>get(OS) +
                                QUESTION_MARK +
                                SERVICE +
                                EQUAL +
                                sessionDTO.<String>get(SERVICE) +
                                AND +
                                BUILD_NO +
                                EQUAL +
                                sessionDTO.<Integer>get(BUILD_NO);
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

    private <T> void putValueInSession(String key, T value) {
        Session<SessionDTO> session = SessionContextHolder.get();
        session.getBody().put(key, value);
    }

    @Override
    public BaseResponse<?> doVerify(VerificationRequest verificationRequest) {
        VerificationType verificationType = verificationRequest.getVerificationType();
        switch (verificationType) {
            case VPA:
                MultiValueMap<String, String> verifyVpaRequest = buildPayUInfoRequest(PayUCommand.VERIFY_VPA.getCode(), verificationRequest.getVerifyValue());
                PayUVpaVerificationResponse verificationResponse = getInfoFromPayU(verifyVpaRequest, new TypeReference<PayUVpaVerificationResponse>() {
                });
                if (verificationResponse.getIsVPAValid() == 1)
                    verificationResponse.setValid(true);
                return BaseResponse.<PayUVpaVerificationResponse>builder().body(verificationResponse).status(HttpStatus.OK).build();
            case BIN:
                MultiValueMap<String, String> verifyBinRequest = buildPayUInfoRequest(PayUCommand.CARD_BIN_INFO.getCode(), verificationRequest.getVerifyValue());
                PayUCardInfo payUCardInfo = getInfoFromPayU(verifyBinRequest, new TypeReference<PayUCardInfo>() {
                });
                if (payUCardInfo.getIsDomestic().equalsIgnoreCase("Y"))
                    payUCardInfo.setValid(true);
                return BaseResponse.<PayUCardInfo>builder().body(payUCardInfo).status(HttpStatus.OK).build();
        }
        return BaseResponse.status(false);
    }

    @Override
    public MerchantTransaction getMerchantTransactionDetails(Map<String, String> params) {
        MerchantTransaction.MerchantTransactionBuilder builder = MerchantTransaction.builder().id(params.get(TXN_ID));
        final String tid = params.containsKey(MIGRATED) && Boolean.parseBoolean(params.get(MIGRATED)) ? params.get(MIGRATED_TXN_ID) : params.get(TXN_ID);
        try {
            MultiValueMap<String, String> payUChargingVerificationRequest = this.buildPayUInfoRequest(PayUCommand.VERIFY_PAYMENT.getCode(), tid);
            PayUVerificationResponse<PayUChargingTransactionDetails> payUChargingVerificationResponse = this.getInfoFromPayU(payUChargingVerificationRequest, new TypeReference<PayUVerificationResponse<PayUChargingTransactionDetails>>() {
            });
            builder.request(payUChargingVerificationRequest);
            builder.response(payUChargingVerificationResponse);
            PayUChargingTransactionDetails payUChargingTransactionDetails = payUChargingVerificationResponse.getTransactionDetails(tid);
            payUChargingTransactionDetails.setMigratedTransactionId(tid);
            if (params.containsKey(MIGRATED) && Boolean.parseBoolean(params.get(MIGRATED))) {
                payUChargingVerificationResponse.getTransactionDetails().remove(tid);
                payUChargingVerificationResponse.getTransactionDetails().put(params.get(TXN_ID), payUChargingTransactionDetails);
            }
            builder.externalTransactionId(payUChargingTransactionDetails.getPayUExternalTxnId());
        } catch (HttpStatusCodeException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        }
        return builder.build();
    }

    public UserPreferredPayment getUserPreferredPayments(String uid) {
        String userCredentials = payUMerchantKey + COLON + uid;
        MultiValueMap<String, String> userCardDetailsRequest = buildPayUInfoRequest(PayUCommand.USER_CARD_DETAILS.getCode(), userCredentials);
        PayUUserCardDetailsResponse userCardDetailsResponse = getInfoFromPayU(userCardDetailsRequest, new TypeReference<PayUUserCardDetailsResponse>() {
        });
        Card.Builder cardBuilder = new Card.Builder().paymentCode(PaymentCode.PAYU);
        for (String cardToken : userCardDetailsResponse.getUserCards().keySet()) {
            cardBuilder.cardDetails(Card.CardDetails.builder().cardToken(cardToken).build());
        }
        return UserPreferredPayment.builder()
                .uid(uid)
                .option(cardBuilder.build())
                .build();
    }

    @Override
    public BaseResponse<?> refund(AbstractPaymentRefundRequest request) {
        Transaction refundTransaction = TransactionContext.get();
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        Builder merchantTransactionBuilder = MerchantTransactionEvent.builder(refundTransaction.getIdStr());
        PayUPaymentRefundResponse.PayUPaymentRefundResponseBuilder<?, ?> refundResponseBuilder = PayUPaymentRefundResponse.builder().transactionId(refundTransaction.getIdStr()).uid(refundTransaction.getUid()).planId(refundTransaction.getPlanId()).itemId(refundTransaction.getItemId()).clientAlias(refundTransaction.getClientAlias()).amount(refundTransaction.getAmount()).msisdn(refundTransaction.getMsisdn()).paymentEvent(refundTransaction.getType());
        try {
            PayUPaymentRefundRequest refundRequest = (PayUPaymentRefundRequest) request;
            MultiValueMap<String, String> refundDetails = buildPayUInfoRequest(PayUCommand.CANCEL_REFUND_TRANSACTION.getCode(), refundRequest.getAuthPayUId(), refundTransaction.getIdStr(), String.valueOf(refundTransaction.getAmount()));
            merchantTransactionBuilder.request(refundDetails);
            PayURefundInitResponse refundResponse = getInfoFromPayU(refundDetails, new TypeReference<PayURefundInitResponse>() {
            });
            if (refundResponse.getStatus() == 0) {
                finalTransactionStatus = TransactionStatus.FAILURE;
                eventPublisher.publishEvent(PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(String.valueOf(refundResponse.getStatus())).description(refundResponse.getMessage()).build());
            } else {
                refundResponseBuilder.authPayUId(refundResponse.getAuthPayUId()).requestId(refundResponse.getRequestId());
                merchantTransactionBuilder.externalTransactionId(refundResponse.getRequestId()).response(refundResponse).build();
            }
        } catch (WynkRuntimeException ex) {
            eventPublisher.publishEvent(PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(ex.getErrorCode()).description(ex.getErrorTitle()).build());
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
            eventPublisher.publishEvent(merchantTransactionBuilder.build());
        }
        return BaseResponse.builder().body(refundResponseBuilder.build()).build();
    }

}
