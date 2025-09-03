package in.wynk.payment.service.impl;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.StandardBusinessErrorDetails;
import in.wynk.common.dto.TechnicalErrorDetails;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.EncryptionUtils;
import in.wynk.common.utils.WynkResponseUtils;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.common.enums.BillingCycle;
import in.wynk.payment.common.utils.BillingUtils;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.BaseTDRResponse;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.common.AbstractPreDebitNotificationResponse;
import in.wynk.payment.dto.payu.PayUUpiCollectResponse;
import in.wynk.payment.dto.payu.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.request.charge.upi.UpiPaymentDetails;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.dto.response.ChargingStatusResponse.ChargingStatusResponseBuilder;
import in.wynk.payment.dto.response.payu.*;
import in.wynk.payment.event.PaymentCallbackKafkaMessage;
import in.wynk.payment.event.RecurringKafkaMessage;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.PropertyResolverUtils;
import in.wynk.payment.utils.RecurringTransactionUtils;
import in.wynk.stream.producer.IKafkaEventPublisher;
import in.wynk.stream.service.IDataPlatformKafkaService;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.PlanPeriodDTO;
import in.wynk.subscription.common.message.CancelMandateEvent;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ConnectTimeoutException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.constant.UpiConstants.INTENT;
import static in.wynk.payment.constant.UpiConstants.UPI;
import static in.wynk.payment.core.constant.BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE;
import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.payu.PayUCommand.PAYU_GETTDR;
import static in.wynk.payment.dto.payu.PayUCommand.UPI_MANDATE_REVOKE;
import static in.wynk.payment.dto.payu.PayUConstants.*;

@Slf4j
public class PayUMerchantPaymentService extends AbstractMerchantPaymentStatusService implements
        IMerchantPaymentChargingService<PayUChargingResponse, PayUChargingRequest<?>>,
        IMerchantPaymentCallbackService<AbstractCallbackResponse, PayUCallbackRequestPayload>,
        IMerchantVerificationService, IMerchantTransactionDetailsService,
        IUserPreferredPaymentService<UserCardDetails, PreferredPaymentDetailsRequest<?>>,
        IMerchantPaymentRefundService<PayUPaymentRefundResponse, PayUPaymentRefundRequest>,
        ICancellingRecurringService,
        IMerchantTDRService {

    private final Gson gson;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimiter rateLimiter = RateLimiter.create(6.0);
    private final IMerchantTransactionService merchantTransactionService;
    private final ITransactionManagerService transactionManagerService;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;
    private final IMerchantPaymentCallbackService<AbstractCallbackResponse, PayUCallbackRequestPayload> callbackHandler;

    private final IKafkaEventPublisher<String, CancelMandateEvent> kafkaPublisherService;
    private final RecurringTransactionUtils recurringTransactionUtils;
    private final IDataPlatformKafkaService dataPlatformKafkaService;

    @Value("${payment.merchant.payu.api.info}")
    private String payUInfoApiUrl;
    @Value("${payment.merchant.payu.api.payment}")
    private String payUPaymentApiUrl;
    @Value("${payment.encKey}")
    private String encryptionKey;

    @Value("${payment.merchant.payu.key}")
    private String payUMerchantKey;
    @Value("${payment.merchant.payu.salt}")
    private String payUSalt;


    public PayUMerchantPaymentService (Gson gson, ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher, PaymentCachingService cachingService,
                                       IMerchantTransactionService merchantTransactionService, IErrorCodesCacheService errorCodesCacheServiceImpl,
                                       @Qualifier(EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate, ITransactionManagerService transactionManagerService,
                                       IRecurringPaymentManagerService recurringPaymentManagerService,
                                       IKafkaEventPublisher<String, CancelMandateEvent> kafkaPublisherService, RecurringTransactionUtils recurringTransactionUtils, IDataPlatformKafkaService dataPlatformKafkaService) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.gson = gson;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.eventPublisher = eventPublisher;
        this.cachingService = cachingService;
        this.kafkaPublisherService = kafkaPublisherService;
        this.callbackHandler = new DelegatePayUCallbackHandler();
        this.merchantTransactionService = merchantTransactionService;
        this.transactionManagerService = transactionManagerService;
        this.recurringPaymentManagerService = recurringPaymentManagerService;
        this.recurringTransactionUtils = recurringTransactionUtils;
        this.dataPlatformKafkaService = dataPlatformKafkaService;
    }

    @Override
    public WynkResponseEntity<AbstractCallbackResponse> handleCallback (PayUCallbackRequestPayload callbackRequest) {
        return callbackHandler.handleCallback(callbackRequest);
    }

    @Override
    public PayUCallbackRequestPayload parseCallback (Map<String, Object> payload) {
        return callbackHandler.parseCallback(payload);
    }

    @Override
    public WynkResponseEntity<PayUChargingResponse> charge (PayUChargingRequest<?> chargingRequest) {
        final WynkResponseEntity.WynkResponseEntityBuilder<PayUChargingResponse> builder = WynkResponseEntity.builder();
        try {
            final Transaction transaction = TransactionContext.get();
            final Map<String, String> payUPayload = getPayload(chargingRequest);
            String encryptedParams;
            if (UpiPaymentDetails.class.isAssignableFrom(chargingRequest.getPurchaseDetails().getPaymentDetails().getClass())) {
                final UpiPaymentDetails upiDetails = ((UpiPaymentDetails) chargingRequest.getPurchaseDetails().getPaymentDetails());
                final String bankCode = upiDetails.isIntent() || chargingRequest.isIntent() ? INTENT : UPI;
                try {
                    if (bankCode.equalsIgnoreCase(UPI)) {
                        payUPayload.put(PAYU_VPA, upiDetails.getUpiDetails().getVpa());
                        encryptedParams = EncryptionUtils.encrypt(this.initUpiPayU(payUPayload, bankCode, new TypeReference<PayUUpiCollectResponse>() {
                        }).getResult().getOtpPostUrl(), encryptionKey);
                    } else {
                        encryptedParams = EncryptionUtils.encrypt(this.initUpiPayU(payUPayload, bankCode, new TypeReference<PayUUpiIntentInitResponse>() {
                        }).getDeepLink(chargingRequest.getPurchaseDetails().getPaymentDetails().isAutoRenew()), encryptionKey);
                    }
                } catch (HttpStatusCodeException e) {
                    log.error(PAYU_API_FAILURE, e.getMessage(), e);
                    encryptedParams = EncryptionUtils.encrypt(gson.toJson(payUPayload), encryptionKey);
                }
            } else {
                encryptedParams = EncryptionUtils.encrypt(gson.toJson(payUPayload), encryptionKey);
            }
            builder.data(PayUChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).info(encryptedParams).build());
        } catch (Exception e) {
            final PaymentErrorType errorType = PAYU006;
            builder.error(TechnicalErrorDetails.builder().code(errorType.getErrorCode()).description(errorType.getErrorMessage()).build()).status(errorType.getHttpResponseStatusCode()).success(false);
            log.error(errorType.getMarker(), e.getMessage(), e);
        }
        return builder.build();
    }

    private String getUpdatedTransactionId (String txnId, PaymentRenewal lastRenewal) {
        String updatedTransactionId = txnId;
        if (Objects.nonNull(lastRenewal)) {
            if (StringUtils.isNotBlank(lastRenewal.getInitialTransactionId())) {
                updatedTransactionId = lastRenewal.getInitialTransactionId();
            } else if (StringUtils.isNotBlank(lastRenewal.getLastSuccessTransactionId())) {
                log.error("Initial transaction id is null but not the  last success transaction id {}", lastRenewal.getLastSuccessTransactionId());
                PaymentRenewal lastToLastRenewal = recurringPaymentManagerService.getRenewalById(lastRenewal.getLastSuccessTransactionId());
                if (StringUtils.isNotBlank(lastToLastRenewal.getInitialTransactionId())) {
                    updatedTransactionId = lastToLastRenewal.getInitialTransactionId();
                } else {
                    updatedTransactionId =
                            StringUtils.isNotBlank(lastToLastRenewal.getLastSuccessTransactionId()) ? lastToLastRenewal.getLastSuccessTransactionId() : lastRenewal.getLastSuccessTransactionId();
                }

            }
        }
        return updatedTransactionId;
    }

    private MerchantTransaction getMerchantData (String id) {
        try {
            return merchantTransactionService.getMerchantTransaction(id);
        } catch (Exception e) {
            log.error("Exception occurred while getting data for tid {} from merchant table: {}", id, e.getMessage());
            return null;
        }
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status (AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        final ChargingStatusResponse statusResponse = fetchAndUpdateTransactionFromSource(transactionStatusRequest, transactionStatusRequest.getExtTxnId());
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(statusResponse).build();
    }

    private ChargingStatusResponse fetchAndUpdateTransactionFromSource (AbstractTransactionStatusRequest transactionStatusRequest, String extTxnId) {
        Transaction transaction = TransactionContext.get();
        if (transactionStatusRequest instanceof ChargingTransactionReconciliationStatusRequest) {
            return fetchChargingStatusFromPayUSource(transaction);
        } else if (transactionStatusRequest instanceof RefundTransactionReconciliationStatusRequest) {
            return fetchRefundStatusFromPayUSource(transaction, extTxnId);
        } else {
            throw new WynkRuntimeException("Unknown transaction request class for transaction Id: " + transaction.getIdStr());
        }
    }

    @SneakyThrows
    private ChargingStatusResponse fetchRefundStatusFromPayUSource (Transaction transaction, String extTxnId) {
        syncRefundTransactionFromSource(transaction, extTxnId);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.warn(PAYU_REFUND_STATUS_VERIFICATION, "Refund Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAYU004);
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.warn(PAYU_REFUND_STATUS_VERIFICATION, "Unknown Refund Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAYU003);
        }
        ChargingStatusResponseBuilder<?, ?> responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).tid(transaction.getIdStr()).planId(transaction.getPlanId());
        if (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != PaymentEvent.POINT_PURCHASE) {
            responseBuilder.validity(cachingService.validTillDate(transaction.getPlanId(), transaction.getMsisdn()));
        }
        return responseBuilder.build();
    }

    private void syncRefundTransactionFromSource (Transaction transaction, String refundRequestId) {
        Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            MultiValueMap<String, String> payURefundStatusRequest = this.buildPayUInfoRequest(transaction.getClientAlias(), PayUCommand.CHECK_ACTION_STATUS.getCode(), refundRequestId);
            merchantTransactionEventBuilder.request(payURefundStatusRequest);
            PayUVerificationResponse<Map<String, PayURefundTransactionDetails>> payUPaymentRefundResponse =
                    this.getInfoFromPayU(payURefundStatusRequest, new TypeReference<PayUVerificationResponse<Map<String, PayURefundTransactionDetails>>>() {
                    });
            merchantTransactionEventBuilder.response(payUPaymentRefundResponse);
            Map<String, PayURefundTransactionDetails> payURefundTransactionDetails = payUPaymentRefundResponse.getTransactionDetails(refundRequestId);
            merchantTransactionEventBuilder.externalTransactionId(payURefundTransactionDetails.get(refundRequestId).getRequestId());
            AnalyticService.update(EXTERNAL_TRANSACTION_ID, payURefundTransactionDetails.get(refundRequestId).getRequestId());
            payURefundTransactionDetails.put(transaction.getIdStr(), payURefundTransactionDetails.get(refundRequestId));
            payURefundTransactionDetails.remove(refundRequestId);
            syncTransactionWithSourceResponse(transaction,
                    PayUVerificationResponse.<PayURefundTransactionDetails>builder().transactionDetails(payURefundTransactionDetails).message(payUPaymentRefundResponse.getMessage())
                            .status(payUPaymentRefundResponse.getStatus()).build());
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            log.error(PAYU_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        }

        if (EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION, PaymentEvent.MANDATE).contains(transaction.getType())) {
            syncChargingTransactionFromSource(transaction, Optional.empty());
        } else {
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    @SneakyThrows
    private ChargingStatusResponse fetchChargingStatusFromPayUSource (Transaction transaction) {
        syncChargingTransactionFromSource(transaction, Optional.empty());
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.warn(PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAYU004);
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.warn(PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAYU003);
        }
        ChargingStatusResponseBuilder<?, ?> responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus()).tid(transaction.getIdStr()).planId(transaction.getPlanId());
        if (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != PaymentEvent.POINT_PURCHASE) {
            responseBuilder.validity(cachingService.validTillDate(transaction.getPlanId(), transaction.getMsisdn()));
        }
        return responseBuilder.build();
    }

    public PayUVerificationResponse<PayUChargingTransactionDetails> syncChargingTransactionFromSource (Transaction transaction,
                                                                                                       Optional<PayUVerificationResponse<PayUChargingTransactionDetails>> verifyOption) {
        final Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        PayUVerificationResponse<PayUChargingTransactionDetails> payUChargingVerificationResponse;
        try {
            final MultiValueMap<String, String> payUChargingVerificationRequest =
                    this.buildPayUInfoRequest(transaction.getClientAlias(), PayUCommand.VERIFY_PAYMENT.getCode(), transaction.getId().toString());
            merchantTransactionEventBuilder.request(payUChargingVerificationRequest);
            payUChargingVerificationResponse =
                    verifyOption.orElseGet(() -> getInfoFromPayU(payUChargingVerificationRequest, new TypeReference<PayUVerificationResponse<PayUChargingTransactionDetails>>() {
                    }));
            merchantTransactionEventBuilder.response(payUChargingVerificationResponse);
            final PayUChargingTransactionDetails payUChargingTransactionDetails = payUChargingVerificationResponse.getTransactionDetails(transaction.getId().toString());
            if (StringUtils.isNotEmpty(payUChargingTransactionDetails.getMode())) {
                AnalyticService.update(PAYMENT_MODE, payUChargingTransactionDetails.getMode());
            }
            if (StringUtils.isNotEmpty(payUChargingTransactionDetails.getBankCode())) {
                AnalyticService.update(BANK_CODE, payUChargingTransactionDetails.getBankCode());
            }
            if (StringUtils.isNotEmpty(payUChargingTransactionDetails.getCardType())) {
                AnalyticService.update(PAYU_CARD_TYPE, payUChargingTransactionDetails.getCardType());
            }
            merchantTransactionEventBuilder.externalTransactionId(payUChargingTransactionDetails.getPayUExternalTxnId());
            AnalyticService.update(EXTERNAL_TRANSACTION_ID, payUChargingTransactionDetails.getPayUExternalTxnId());
            syncTransactionWithSourceResponse(transaction, payUChargingVerificationResponse);
            if (transaction.getStatus() == TransactionStatus.FAILURE) {
                if (!StringUtils.isEmpty(payUChargingTransactionDetails.getErrorCode()) || !StringUtils.isEmpty(payUChargingTransactionDetails.getTransactionFailureReason())) {
                    final String failureReason = payUChargingTransactionDetails.getTransactionFailureReason();
                    recurringTransactionUtils.cancelRenewalBasedOnErrorReason(failureReason, transaction);
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(payUChargingTransactionDetails.getErrorCode()).description(failureReason).build());
                }
            }
        } catch (HttpStatusCodeException e) {
            merchantTransactionEventBuilder.response(e.getResponseBodyAsString());
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } catch (Exception e) {
            log.error(PAYU_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        }
        eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        return payUChargingVerificationResponse;
    }

    private void syncTransactionWithSourceResponse (Transaction transaction, PayUVerificationResponse<? extends AbstractPayUTransactionDetails> transactionDetailsWrapper) {
        TransactionStatus finalTransactionStatus = TransactionStatus.UNKNOWN;
        int retryInterval = (transaction.getType() == PaymentEvent.POINT_PURCHASE) ? 1 : cachingService.getPlan(transaction.getPlanId()).getPeriod().getRetryInterval();
        if (transactionDetailsWrapper.getStatus() == 1) {
            final AbstractPayUTransactionDetails transactionDetails = transactionDetailsWrapper.getTransactionDetails(transaction.getIdStr());
            if (SUCCESS.equalsIgnoreCase(transactionDetails.getStatus())) {
                /**
                 * PayU check to verify whether mandate transaction is successfully registered with standing instruction sist,
                 * otherwise consider it as normal transaction without mandate
                 * */
                if (EnumSet.of(PaymentEvent.SUBSCRIBE).contains(transaction.getType()) && PayUChargingTransactionDetails.class.isAssignableFrom(transactionDetails.getClass())) {
                    final PayUChargingTransactionDetails chargingDetails = (PayUChargingTransactionDetails) transactionDetails;
                    if (!PAYU_PAYMENT_SOURCE_SIST.equalsIgnoreCase(chargingDetails.getPaymentSource())) {
                        transaction.setType(PaymentEvent.PURCHASE.getValue());
                        transaction.setMandateAmount(-1);
                        AnalyticService.update(PAYU_PAYMENT_SOURCE_SIST, chargingDetails.getPaymentSource());
                        log.warn(PAYU_CHARGING_STATUS_VERIFICATION, "transaction was initiated with auto-pay but si is not captured.");
                    }
                }
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (FAILURE.equalsIgnoreCase(transactionDetails.getStatus()) || (FAILED.equalsIgnoreCase(transactionDetails.getStatus())) ||
                    PAYU_STATUS_NOT_FOUND.equalsIgnoreCase(transactionDetails.getStatus())) {
                if (AUTO_REFUND.equals(((PayUChargingTransactionDetails) transactionDetails).getUnMappedStatus())) {
                    finalTransactionStatus = TransactionStatus.AUTO_REFUND;
                } else {
                    finalTransactionStatus = TransactionStatus.FAILURE;
                }
            } else if ((transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - (ONE_DAY_IN_MILLI * retryInterval)) &&
                    (StringUtils.equalsIgnoreCase(PENDING, transactionDetails.getStatus()) ||
                            (transaction.getType() == PaymentEvent.REFUND && StringUtils.equalsIgnoreCase(QUEUED, transactionDetails.getStatus())))) {
                finalTransactionStatus = TransactionStatus.INPROGRESS;
            } else if ((transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - (ONE_DAY_IN_MILLI * retryInterval)) &&
                    StringUtils.equalsIgnoreCase(PENDING, transactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.INPROGRESS;
            }
        } else {
            finalTransactionStatus = TransactionStatus.FAILURE;
        }
        transaction.setStatus(finalTransactionStatus.getValue());
    }

    private Map<String, String> getPayload (PayUChargingRequest<?> chargingRequest) {
        final Transaction transaction = TransactionContext.get();
        final int planId = transaction.getPlanId();
        final PlanDTO selectedPlan = cachingService.getPlan(planId);
        double finalPlanAmount = transaction.getAmount();
        String uid = transaction.getUid();
        String msisdn = transaction.getMsisdn();
        final String email = uid + BASE_USER_EMAIL;
        final String payUMerchantKey = PropertyResolverUtils.resolve(transaction.getClientAlias(), transaction.getPaymentChannel().getCode().toLowerCase(), MERCHANT_ID);
        String userCredentials = payUMerchantKey + COLON + uid;
        Map<String, String> payloadTemp;
        if (transaction.getType() == PaymentEvent.SUBSCRIBE || transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
            //payloadTemp = buildPayUForm(transaction.getClientAlias(), transaction.getId(), email, uid, planId, finalPlanAmount);
            payloadTemp = getPayload(transaction.getClientAlias(), transaction.getId(), email, uid, planId, finalPlanAmount, transaction, transaction.getType());
        } else {
            payloadTemp = getPayload(transaction.getClientAlias(), transaction.getId(), email, uid, planId, finalPlanAmount);
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
        payload.put(PAYU_SUCCESS_URL, ((IChargingDetails) chargingRequest.getPurchaseDetails()).getCallbackDetails().getCallbackUrl());
        payload.put(PAYU_FAILURE_URL, ((IChargingDetails) chargingRequest.getPurchaseDetails()).getCallbackDetails().getCallbackUrl());
        // Not in document
        payload.put(PAYU_IS_FALLBACK_ATTEMPT, String.valueOf(false));
        payload.put(ERROR, PAYU_REDIRECT_MESSAGE);
        payload.put(PAYU_USER_CREDENTIALS, userCredentials);
        return payload;
    }

    private Map<String, String> getPayload (String client, UUID transactionId, String email, String uid, int planId, double finalPlanAmount) {
        Map<String, String> payload = new HashMap<>();
        String udf1 = StringUtils.EMPTY;
        String reqType = PaymentRequestType.DEFAULT.name();
        String checksumHash = getChecksumHashForPayment(client, transactionId, udf1, email, uid, String.valueOf(planId), finalPlanAmount);
        payload.put(PAYU_HASH, checksumHash);
        payload.put(PAYU_REQUEST_TYPE, reqType);
        payload.put(PAYU_UDF1_PARAMETER, udf1);
        return payload;
    }

    //Not used therefore commenting as of now
    private Map<String, String> getPayload (String client, UUID transactionId, String email, String uid, int planId, double finalPlanAmount, Transaction transaction, PaymentEvent paymentEvent) {
        Map<String, String> payload = new HashMap<>();
        String reqType = PaymentRequestType.SUBSCRIBE.name();
        String udf1 = PAYU_SI_KEY.toUpperCase();
        Calendar cal = Calendar.getInstance();
        Date today = cal.getTime();
        cal.add(Calendar.YEAR, 5); // 5 yrs from now
        Date next5Year = cal.getTime();
        boolean isFreeTrial = paymentEvent == PaymentEvent.TRIAL_SUBSCRIPTION;
        BillingUtils billingUtils = new BillingUtils(1, BillingCycle.ADHOC);
        try {
            String siDetails = objectMapper.writeValueAsString(new SiDetails(billingUtils.getBillingCycle(), billingUtils.getBillingInterval(), transaction.getMandateAmount(), today, next5Year));
            String checksumHash = getChecksumHashForPayment(client, transactionId, udf1, email, uid, String.valueOf(planId), finalPlanAmount, siDetails);
            payload.put(PAYU_SI_KEY, "1");
            payload.put(PAYU_API_VERSION, "7");
            payload.put(PAYU_HASH, checksumHash);
            payload.put(PAYU_UDF1_PARAMETER, udf1);
            payload.put(PAYU_SI_DETAILS, siDetails);
            payload.put(PAYU_REQUEST_TYPE, reqType);
            payload.put(PAYU_FREE_TRIAL, isFreeTrial ? "1" : "0");
        } catch (Exception e) {
            log.error("Error Creating SiDetails Object");
        }
        return payload;
    }

    private <T> T initUpiPayU (Map<String, String> payUPayload, String bankCode, TypeReference<T> target) {
        try {
            MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
            for (String key : payUPayload.keySet()) {
                requestMap.add(key, payUPayload.get(key));
            }
            payUPayload.clear();
            requestMap.add(PAYU_PG, "UPI");
            requestMap.add(PAYU_TXN_S2S_FLOW, "4");
            requestMap.add(PAYU_BANKCODE, bankCode);
            HttpHeaders headers = new HttpHeaders();
            headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE);
            headers.add(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);
            final RequestEntity entity = RequestEntity.method(HttpMethod.POST, URI.create(payUPaymentApiUrl)).headers(headers).body(requestMap);
            final String response = restTemplate.exchange(entity, String.class).getBody();
            return objectMapper.readValue(response, target);
        } catch (Exception ex) {
            log.error(PAYU_API_FAILURE, ex.getMessage(), ex);
            throw new WynkRuntimeException(PAYU006, ex);
        }
    }

    private <T> T getInfoFromPayU (MultiValueMap<String, String> request, TypeReference<T> target) {
        try {
            final String response = restTemplate.exchange(RequestEntity.method(HttpMethod.POST, URI.create(payUInfoApiUrl)).body(request), String.class).getBody();
            if (StringUtils.isNotEmpty(response) && response.contains("Record not found")) {
                throw new WynkRuntimeException("Record not found");
            }
            return objectMapper.readValue(response, target);
        } catch (HttpStatusCodeException ex) {
            log.error(PAYU_API_FAILURE, ex.getResponseBodyAsString(), ex);
            throw new WynkRuntimeException(PAYU006, ex);
        } catch (Exception ex) {
            log.error(PAYU_API_FAILURE, ex.getMessage(), ex);
            throw new WynkRuntimeException(PAYU006, ex);
        }
    }

    private MultiValueMap<String, String> buildPayUInfoRequest (String client, String command, String var1, String... vars) {
        final String payUMerchantKey = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
        final String payUMerchantSecret = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_SECRET);
        String hash = generateHashForPayUApi(payUMerchantKey, payUMerchantSecret, command, var1);
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

    private String generateHashForPayUApi (String payUMerchantKey, String payUSalt, String command, String var1) {
        String builder = payUMerchantKey + PIPE_SEPARATOR + command + PIPE_SEPARATOR + var1 + PIPE_SEPARATOR + payUSalt;
        return EncryptionUtils.generateSHA512Hash(builder);
    }

    private String getChecksumHashForPayment (String client, UUID transactionId, String udf1, String email, String firstName, String planTitle, double amount, String siDetails) {
        final String payUMerchantKey = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
        final String payUMerchantSecret = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_SECRET);
        String rawChecksum = payUMerchantKey + PIPE_SEPARATOR + transactionId.toString() + PIPE_SEPARATOR + amount + PIPE_SEPARATOR + planTitle + PIPE_SEPARATOR + firstName + PIPE_SEPARATOR + email +
                PIPE_SEPARATOR + udf1 + "||||||||||" + siDetails + PIPE_SEPARATOR + payUMerchantSecret;
        return EncryptionUtils.generateSHA512Hash(rawChecksum);
    }

    private String getChecksumHashForPayment (String client, UUID transactionId, String udf1, String email, String firstName, String planTitle, double amount) {
        final String payUMerchantKey = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
        final String payUMerchantSecret = PropertyResolverUtils.resolve(client, PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_SECRET);
        String rawChecksum = payUMerchantKey + PIPE_SEPARATOR + transactionId.toString() + PIPE_SEPARATOR + amount + PIPE_SEPARATOR + planTitle + PIPE_SEPARATOR + firstName + PIPE_SEPARATOR + email +
                PIPE_SEPARATOR + udf1 + "||||||||||" + payUMerchantSecret;
        return EncryptionUtils.generateSHA512Hash(rawChecksum);
    }

    @SneakyThrows
    private boolean validateCallbackChecksum (String payUMerchantKey, String payUSalt, String transactionId, String transactionStatus, String udf, String email, String firstName, String planTitle,
                                              double amount, String payUResponseHash) {
        DecimalFormat df = new DecimalFormat("#0.00");
        String generatedString =
                payUSalt + PIPE_SEPARATOR + transactionStatus + "||||||" + udf + PIPE_SEPARATOR + URLDecoder.decode(String.valueOf(email), String.valueOf(StandardCharsets.UTF_8)) + PIPE_SEPARATOR +
                        firstName + PIPE_SEPARATOR + planTitle + PIPE_SEPARATOR + df.format(amount) + PIPE_SEPARATOR + transactionId + PIPE_SEPARATOR + payUMerchantKey;
        final String generatedHash = EncryptionUtils.generateSHA512Hash(generatedString);
        assert generatedHash != null;
        return generatedHash.equals(payUResponseHash);
    }

    @SneakyThrows
    @Override
    public WynkResponseEntity<IVerificationResponse> doVerify (AbstractVerificationRequest verificationRequest) {
        switch (verificationRequest.getVerificationType()) {
            case VPA:
                MultiValueMap<String, String> verifyVpaRequest = buildPayUInfoRequest(verificationRequest.getClient(), PayUCommand.VERIFY_VPA.getCode(), verificationRequest.getVerifyValue(),
                        objectMapper.writeValueAsString(new HashMap<String, String>() {{
                            put("validateAutoPayVPA", "1");
                        }}));
                PayUVpaVerificationResponse verificationResponse = getInfoFromPayU(verifyVpaRequest, new TypeReference<PayUVpaVerificationResponse>() {
                });
                if (verificationResponse.getIsVPAValid() == 1) {
                    verificationResponse.setValid(true);
                }
                return WynkResponseEntity.<IVerificationResponse>builder().data(verificationResponse).status(HttpStatus.OK).build();
            case BIN:
                MultiValueMap<String, String> verifyBinRequest =
                        buildPayUInfoRequest(verificationRequest.getClient(), PayUCommand.CARD_BIN_INFO.getCode(), "1", new String[]{verificationRequest.getVerifyValue(), null, null, "1"});
                PayUCardInfo cardInfo;
                try {
                    PayUBinWrapper<PayUCardInfo> payUBinWrapper = getInfoFromPayU(verifyBinRequest, new TypeReference<PayUBinWrapper<PayUCardInfo>>() {
                    });
                    cardInfo = payUBinWrapper.getBin();
                } catch (WynkRuntimeException e) {
                    cardInfo = new PayUCardInfo();
                    cardInfo.setValid(Boolean.FALSE);
                    cardInfo.setIssuingBank(UNKNOWN.toUpperCase());
                    cardInfo.setCardType(UNKNOWN.toUpperCase());
                    cardInfo.setCardCategory(UNKNOWN.toUpperCase());
                }
                return WynkResponseEntity.<IVerificationResponse>builder().data(cardInfo).status(cardInfo.isValid() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).build();
            default:
                return WynkResponseEntity.<IVerificationResponse>builder().build();
        }
    }

    @Override
    public MerchantTransaction getMerchantTransactionDetails (Map<String, String> params) {
        MerchantTransaction.MerchantTransactionBuilder builder = MerchantTransaction.builder().id(params.get(TXN_ID));
        final String tid = params.containsKey(MIGRATED) && Boolean.parseBoolean(params.get(MIGRATED)) ? params.get(MIGRATED_TXN_ID) : params.get(TXN_ID);
        try {
            MultiValueMap<String, String> payUChargingVerificationRequest = this.buildPayUInfoRequest(TransactionContext.get().getClientAlias(), PayUCommand.VERIFY_PAYMENT.getCode(), tid);
            PayUVerificationResponse<PayUChargingTransactionDetails> payUChargingVerificationResponse =
                    this.getInfoFromPayU(payUChargingVerificationRequest, new TypeReference<PayUVerificationResponse<PayUChargingTransactionDetails>>() {
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

    @Override
    @ClientAware(clientAlias = "#request.clientAlias")
    public WynkResponseEntity<UserCardDetails> getUserPreferredPayments (PreferredPaymentDetailsRequest request) {
        WynkResponseEntity.WynkResponseEntityBuilder<UserCardDetails> builder = WynkResponseEntity.builder();
        final String payUMerchantKey = PropertyResolverUtils.resolve(request.getClientAlias(), PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
        String userCredentials = payUMerchantKey + COLON + request.getPreferredPayment().getId().getUid();
        MultiValueMap<String, String> userCardDetailsRequest = buildPayUInfoRequest(request.getClientAlias(), PayUCommand.USER_CARD_DETAILS.getCode(), userCredentials);
        PayUUserCardDetailsResponse userCardDetailsResponse = getInfoFromPayU(userCardDetailsRequest, new TypeReference<PayUUserCardDetailsResponse>() {
        });
        Map<String, CardDetails> cardDetailsMap = userCardDetailsResponse.getUserCards();
        if (cardDetailsMap.isEmpty()) {
            builder.error(TechnicalErrorDetails.builder().code(PAY203.getErrorCode()).description(PAY203.getErrorMessage()).build()).success(false);
        }
        return builder.data(UserCardDetails.builder().cards(cardDetailsMap).build()).build();
    }

    @Override
    public WynkResponseEntity<PayUPaymentRefundResponse> refund (PayUPaymentRefundRequest refundRequest) {
        Transaction refundTransaction = TransactionContext.get();
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        Builder merchantTransactionBuilder = MerchantTransactionEvent.builder(refundTransaction.getIdStr());
        WynkResponseEntity.WynkResponseEntityBuilder<PayUPaymentRefundResponse> responseBuilder = WynkResponseEntity.builder();
        PayUPaymentRefundResponse.PayUPaymentRefundResponseBuilder<?, ?> refundResponseBuilder =
                PayUPaymentRefundResponse.builder().transactionId(refundTransaction.getIdStr()).uid(refundTransaction.getUid()).planId(refundTransaction.getPlanId())
                        .itemId(refundTransaction.getItemId()).clientAlias(refundTransaction.getClientAlias()).amount(refundTransaction.getAmount()).msisdn(refundTransaction.getMsisdn())
                        .paymentEvent(refundTransaction.getType());
        String authPayUId = refundRequest.getAuthPayUId();
        if (authPayUId == null) {
            Transaction oldTransaction = transactionManagerService.get(refundRequest.getOriginalTransactionId());
            PayUVerificationResponse<PayUChargingTransactionDetails> currentStatus = syncChargingTransactionFromSource(oldTransaction, Optional.empty());
            authPayUId = currentStatus.getTransactionDetails(oldTransaction.getIdStr()).getPayUExternalTxnId();
        }
        try {
            MultiValueMap<String, String> refundDetails =
                    buildPayUInfoRequest(refundTransaction.getClientAlias(), PayUCommand.CANCEL_REFUND_TRANSACTION.getCode(), authPayUId, refundTransaction.getIdStr(),
                            String.valueOf(refundTransaction.getAmount()));
            merchantTransactionBuilder.request(refundDetails);
            PayURefundInitResponse refundResponse = getInfoFromPayU(refundDetails, new TypeReference<PayURefundInitResponse>() {
            });
            if (refundResponse.getStatus() == 0) {
                finalTransactionStatus = TransactionStatus.FAILURE;
                PaymentErrorEvent errorEvent =
                        PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(String.valueOf(refundResponse.getStatus())).description(refundResponse.getMessage()).build();
                responseBuilder.success(false).error(StandardBusinessErrorDetails.builder().code(errorEvent.getCode()).description(errorEvent.getDescription()).build());
                eventPublisher.publishEvent(errorEvent);
            } else {
                refundResponseBuilder.authPayUId(refundResponse.getAuthPayUId()).requestId(refundResponse.getRequestId());
                merchantTransactionBuilder.externalTransactionId(refundResponse.getRequestId()).response(refundResponse).build();
            }
        } catch (WynkRuntimeException ex) {
            PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(ex.getErrorCode()).description(ex.getErrorTitle()).build();
            responseBuilder.success(false).status(ex.getErrorType().getHttpResponseStatusCode())
                    .error(TechnicalErrorDetails.builder().code(errorEvent.getCode()).description(errorEvent.getDescription()).build());
            eventPublisher.publishEvent(errorEvent);
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
            eventPublisher.publishEvent(merchantTransactionBuilder.build());
        }
        return responseBuilder.data(refundResponseBuilder.build()).build();
    }


    @Override
    public void cancelRecurring (String transactionId, PaymentEvent paymentEvent) {
        try {
            LinkedHashMap<String, String> orderedMap = new LinkedHashMap<>();
            PaymentRenewal lastRenewal = recurringPaymentManagerService.getRenewalById(transactionId);
            String txnId = getUpdatedTransactionId(transactionId, lastRenewal);
            MerchantTransaction merchantTransaction = getMerchantData(txnId);
            assert merchantTransaction != null;
            orderedMap.put(PAYU_RESPONSE_AUTH_PAYUID, merchantTransaction.getExternalTransactionId());
            orderedMap.put(PAYU_REQUEST_ID, transactionId.replace("-", ""));
            String variable = gson.toJson(orderedMap);
            ITransactionManagerService transactionManagerService = BeanLocatorFactory.getBean(ITransactionManagerService.class);
            Transaction transaction= transactionManagerService.get(transactionId);
            MultiValueMap<String, String> requestMap = buildPayUInfoRequest(transactionManagerService.get(transactionId).getClientAlias(), UPI_MANDATE_REVOKE.getCode(), variable);
            PayUBaseResponse response = this.getInfoFromPayU(requestMap, new TypeReference<PayUBaseResponse>() {
            });
            AnalyticService.update(MANDATE_REVOKE_RESPONSE, gson.toJson(response));
            CancelMandateEvent mandateEvent= CancelMandateEvent.builder().paymentEvent(paymentEvent).planId(transaction.getPlanId()).msisdn(transaction.getMsisdn()).uid(transaction.getUid()).paymentCode(transaction.getPaymentChannel().getCode().toUpperCase()).payUCancellationResponse(CancelMandateEvent.PayUBaseResponse.builder().action(response.getAction()).status(response.getStatus()).message(response.getMessage()).build()).build();
            dataPlatformKafkaService.publish(RecurringKafkaMessage.from(transactionId, paymentEvent, PAYU));
            kafkaPublisherService.publish(mandateEvent);
        } catch (Exception e) {
            log.error(PAYU_UPI_MANDATE_REVOKE_ERROR, e.getMessage());
            throw new WynkRuntimeException(PAY112);
        }
    }

    @Override
    public BaseTDRResponse getTDR (String transactionId) {
        try {
            final Transaction transaction = TransactionContext.get();
            final MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(transactionId);
            final String midPayId = merchantTransaction.getExternalTransactionId();
            final MultiValueMap<String, String> requestMap = buildPayUInfoRequest(transaction.getClientAlias(), PAYU_GETTDR.getCode(), midPayId);
            final PayUTdrResponse response = this.getInfoFromPayU(requestMap, new TypeReference<PayUTdrResponse>() {
            });
            AnalyticService.update("Tdr response from payu", String.valueOf(response));
            return BaseTDRResponse.from(response.getMessage().getTdr());
        } catch (Exception e) {
            log.error(PAYU_TDR_ERROR, e.getMessage());
        }
        return BaseTDRResponse.from(-2);
    }

    private class DelegatePayUCallbackHandler implements IMerchantPaymentCallbackService<AbstractCallbackResponse, PayUCallbackRequestPayload> {

        private final Map<Class<? extends IMerchantPaymentCallbackService<? extends AbstractCallbackResponse, ? extends PayUCallbackRequestPayload>>, IMerchantPaymentCallbackService> delegator =
                new HashMap<>();

        public DelegatePayUCallbackHandler () {
            this.delegator.put(GenericPayUCallbackHandler.class, new GenericPayUCallbackHandler());
            this.delegator.put(RefundPayUCallBackHandler.class, new RefundPayUCallBackHandler());
        }

        @Override
        public WynkResponseEntity<AbstractCallbackResponse> handleCallback (PayUCallbackRequestPayload callbackRequest) {
            final Transaction transaction = TransactionContext.get();
            final String transactionId = transaction.getIdStr();
            try {
                final String errorCode = callbackRequest.getError();
                final String errorMessage = callbackRequest.getErrorMessage();
                final IMerchantPaymentCallbackService callbackService =
                        delegator.get(PayUAutoRefundCallbackRequestPayload.class.isAssignableFrom(callbackRequest.getClass()) ? RefundPayUCallBackHandler.class : GenericPayUCallbackHandler.class);
                if (PayUAutoRefundCallbackRequestPayload.class.isAssignableFrom(callbackRequest.getClass()) || callbackService.validate(callbackRequest)) {
                    return callbackService.handleCallback(callbackRequest);
                } else {
                    log.error(PAYU_CHARGING_CALLBACK_FAILURE,
                            "Invalid checksum found with transactionStatus: {}, Wynk transactionId: {}, PayU transactionId: {}, Reason: error code: {}, error message: {} for uid: {}",
                            callbackRequest.getStatus(), transactionId, callbackRequest.getExternalTransactionId(), errorCode, errorMessage, transaction.getUid());
                    throw new PaymentRuntimeException(PaymentErrorType.PAY302, "Invalid checksum found with transaction id:" + transactionId);
                }
            } catch (Exception e) {
                throw new PaymentRuntimeException(PaymentErrorType.PAY302, e);
            }
        }

        @Override
        public PayUCallbackRequestPayload parseCallback (Map<String, Object> payload) {
            try {
                if (Objects.nonNull(payload.get("action")) && payload.get("action").equals("refund")){
                    final String json = objectMapper.writeValueAsString(payload);
                    return objectMapper.readValue(json, PayUAutoRefundCallbackRequestPayload.class);
                } else {
                    final String json = objectMapper.writeValueAsString(payload);
                    return objectMapper.readValue(json, PayUCallbackRequestPayload.class);
                }
            } catch (Exception e) {
                log.error(CALLBACK_PAYLOAD_PARSING_FAILURE, "Unable to parse callback payload due to {}", e.getMessage(), e);
                throw new WynkRuntimeException(PAY006, e);
            }
        }

        private class GenericPayUCallbackHandler implements IMerchantPaymentCallbackService<AbstractCallbackResponse, PayUCallbackRequestPayload> {

            @Override
            public WynkResponseEntity<AbstractCallbackResponse> handleCallback (PayUCallbackRequestPayload callbackRequest) {
                final Transaction transaction = TransactionContext.get();
                syncChargingTransactionFromSource(transaction, Optional.of(PayUVerificationResponse.<PayUChargingTransactionDetails>builder().status(1)
                        .transactionDetails(Collections.singletonMap(transaction.getIdStr(), AbstractPayUTransactionDetails.from(callbackRequest))).build()));
                if (!EnumSet.of(PaymentEvent.RENEW, PaymentEvent.REFUND).contains(transaction.getType())) {
                    Optional<IPurchaseDetails> optionalDetails = TransactionContext.getPurchaseDetails();
                    if (optionalDetails.isPresent()) {
                        final String redirectionUrl;
                        IChargingDetails chargingDetails = (IChargingDetails) optionalDetails.get();
                        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                            log.warn(PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(),
                                    transaction.getId().toString());
                            redirectionUrl = chargingDetails.getPageUrlDetails().getPendingPageUrl();
                        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                            log.warn(PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                            redirectionUrl = chargingDetails.getPageUrlDetails().getUnknownPageUrl();
                        } else if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                            redirectionUrl = chargingDetails.getPageUrlDetails().getSuccessPageUrl();
                        } else {
                            redirectionUrl = chargingDetails.getPageUrlDetails().getFailurePageUrl();
                        }
                        dataPlatformKafkaService.publish(PaymentCallbackKafkaMessage.from(callbackRequest, transaction));
                        return WynkResponseUtils.redirectResponse(redirectionUrl);
                    }
                }
                dataPlatformKafkaService.publish(PaymentCallbackKafkaMessage.from(callbackRequest, transaction));
                return WynkResponseEntity.<AbstractCallbackResponse>builder().data(DefaultCallbackResponse.builder().transactionStatus(transaction.getStatus()).build()).build();
            }

            @Override
            public boolean validate (PayUCallbackRequestPayload callbackRequest) {
                final Transaction transaction = TransactionContext.get();
                final String transactionId = transaction.getIdStr();
                final String payUMerchantKey = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
                final String payUMerchantSecret = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_SECRET);
                String productInfo = Optional.ofNullable(callbackRequest.getProductInfo()).orElse(String.valueOf(transaction.getPlanId()));
                return validateCallbackChecksum(payUMerchantKey, payUMerchantSecret, transactionId, callbackRequest.getStatus(), callbackRequest.getUdf(), callbackRequest.getEmail(),
                        callbackRequest.getFirstName(), productInfo, transaction.getAmount(), callbackRequest.getResponseHash());
            }

        }

        private class RefundPayUCallBackHandler implements IMerchantPaymentCallbackService<AbstractCallbackResponse, PayUAutoRefundCallbackRequestPayload> {

            @Override
            public WynkResponseEntity<AbstractCallbackResponse> handleCallback (PayUAutoRefundCallbackRequestPayload callbackRequest) {
                final Transaction transaction = TransactionContext.get();
                syncRefundTransactionFromSource(transaction, callbackRequest.getRequestId());
                // if an auto refund transaction is successful after recon from payu then transaction status should be marked as auto refunded
                if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                    transaction.setStatus(TransactionStatus.REFUNDED.getValue());
                }
                dataPlatformKafkaService.publish(PaymentCallbackKafkaMessage.from(callbackRequest,transaction));
                return WynkResponseEntity.<AbstractCallbackResponse>builder().data(DefaultCallbackResponse.builder().transactionStatus(transaction.getStatus()).build()).build();
            }

            @Override
            public boolean validate (PayUAutoRefundCallbackRequestPayload callbackRequest) {
                final Transaction transaction = TransactionContext.get();
                final String transactionId = transaction.getIdStr();
                final String payUMerchantKey = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_ID);
                final String payUMerchantSecret = PropertyResolverUtils.resolve(transaction.getClientAlias(), PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(), MERCHANT_SECRET);
                String productInfo = Optional.ofNullable(callbackRequest.getProductInfo()).orElse(String.valueOf(transaction.getPlanId()));
                return validateCallbackChecksum(payUMerchantKey, payUMerchantSecret, transactionId, callbackRequest.getStatus(), callbackRequest.getUdf(), callbackRequest.getEmail(),
                        callbackRequest.getFirstName(), productInfo, transaction.getAmount(), callbackRequest.getResponseHash());
            }
        }
    }

}