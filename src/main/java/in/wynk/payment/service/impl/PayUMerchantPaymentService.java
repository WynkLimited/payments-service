package in.wynk.payment.service.impl;

import com.datastax.driver.core.utils.UUIDs;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import in.wynk.common.dto.ICacheService;
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
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.*;
import in.wynk.payment.core.event.MerchantTransactionEvent;
import in.wynk.payment.core.event.MerchantTransactionEvent.Builder;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.IChargingDetails;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.payu.*;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.*;
import in.wynk.payment.dto.response.ChargingStatusResponse.ChargingStatusResponseBuilder;
import in.wynk.payment.dto.response.payu.*;
import in.wynk.payment.exception.PaymentRuntimeException;
import in.wynk.payment.service.*;
import in.wynk.payment.utils.PropertyResolverUtils;
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

import static in.wynk.common.constant.BaseConstants.*;
import static in.wynk.payment.core.constant.BeanConstant.EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE;
import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentErrorType.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.*;
import static in.wynk.payment.dto.payu.PayUCommand.PRE_DEBIT_SI;
import static in.wynk.payment.dto.payu.PayUCommand.UPI_MANDATE_REVOKE;
import static in.wynk.payment.dto.payu.PayUConstants.*;

@Slf4j
@Service(PAYU_MERCHANT_PAYMENT_SERVICE)
public class PayUMerchantPaymentService extends AbstractMerchantPaymentStatusService implements IMerchantPaymentChargingService<PayUChargingResponse, PayUChargingRequest<?>>, IMerchantPaymentCallbackService<AbstractCallbackResponse, PayUCallbackRequestPayload>, IMerchantPaymentRenewalService<PaymentRenewalChargingRequest>, IMerchantVerificationService, IMerchantTransactionDetailsService, IUserPreferredPaymentService<UserCardDetails, PreferredPaymentDetailsRequest<?>>, IMerchantPaymentRefundService<PayUPaymentRefundResponse, PayUPaymentRefundRequest>, IPreDebitNotificationService, ICancellingRecurringService {

    private final Gson gson;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimiter rateLimiter = RateLimiter.create(6.0);
    private final IMerchantTransactionService merchantTransactionService;
    private final ICacheService<PaymentMethod, String> paymentMethodCachingService;

    @Value("${payment.merchant.payu.api.info}")
    private String payUInfoApiUrl;
    @Value("${payment.merchant.payu.api.payment}")
    private String payUPaymentApiUrl;

    public PayUMerchantPaymentService(Gson gson,
                                      ObjectMapper objectMapper,
                                      ApplicationEventPublisher eventPublisher,
                                      PaymentCachingService cachingService,
                                      IMerchantTransactionService merchantTransactionService,
                                      IErrorCodesCacheService errorCodesCacheServiceImpl,
                                      @Qualifier(EXTERNAL_PAYMENT_GATEWAY_S2S_TEMPLATE) RestTemplate restTemplate,
                                      ICacheService<PaymentMethod, String> paymentMethodCachingService) {
        super(cachingService, errorCodesCacheServiceImpl);
        this.gson = gson;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
        this.eventPublisher = eventPublisher;
        this.cachingService = cachingService;
        this.merchantTransactionService = merchantTransactionService;
        this.paymentMethodCachingService = paymentMethodCachingService;
    }

    @Override
    public WynkResponseEntity<AbstractCallbackResponse> handleCallback(PayUCallbackRequestPayload callbackRequest) {
        handleCallbackInternal(callbackRequest);
        final Transaction transaction = TransactionContext.get();
        if (!EnumSet.of(PaymentEvent.RENEW, PaymentEvent.REFUND).contains(transaction.getType())) {
            Optional<IPurchaseDetails> optionalDetails = TransactionContext.getPurchaseDetails();
            if (optionalDetails.isPresent()) {
                final String redirectionUrl;
                IChargingDetails chargingDetails = (IChargingDetails) optionalDetails.get();
                if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
                    log.error(PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    redirectionUrl = chargingDetails.getPageUrlDetails().getPendingPageUrl();
                } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
                    log.error(PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
                    redirectionUrl = chargingDetails.getPageUrlDetails().getUnknownPageUrl();
                } else if (transaction.getStatus() == TransactionStatus.SUCCESS) {
                    redirectionUrl = chargingDetails.getPageUrlDetails().getSuccessPageUrl();
                } else {
                    redirectionUrl = chargingDetails.getPageUrlDetails().getFailurePageUrl();
                }
                return WynkResponseUtils.redirectResponse(redirectionUrl);
            }
        }
        return WynkResponseEntity.<AbstractCallbackResponse>builder().data(DefaultCallbackResponse.builder().transactionStatus(transaction.getStatus()).build()).build();
    }

    @Override
    public PayUCallbackRequestPayload parseCallback(Map<String, Object> payload) {
        try {
            return gson.fromJson(gson.toJsonTree(payload), PayUCallbackRequestPayload.class);
        } catch (Exception e) {
            log.error(CALLBACK_PAYLOAD_PARSING_FAILURE, "Unable to parse callback payload due to {}", e.getMessage(), e);
            throw new WynkRuntimeException(PAY006, e);
        }
    }

    @Override
    public WynkResponseEntity<PayUChargingResponse> charge(PayUChargingRequest<?> chargingRequest) {
        final WynkResponseEntity.WynkResponseEntityBuilder<PayUChargingResponse> builder = WynkResponseEntity.builder();
        try {
            final Transaction transaction = TransactionContext.get();
            final Map<String, String> payUPayload = getPayload(chargingRequest);
            final String encryptedParams;
            final String encryptionKey = PropertyResolverUtils.resolve(transaction.getClientAlias(),transaction.getPaymentChannel().getCode().toLowerCase(),MERCHANT_ENCKEY);
            if (chargingRequest.isIntent()) {
                encryptedParams = EncryptionUtils.encrypt(this.initIntentUpiPayU(payUPayload), encryptionKey);
            } else {
                encryptedParams = EncryptionUtils.encrypt(gson.toJson(payUPayload), encryptionKey);
            }
            builder.data(PayUChargingResponse.builder().tid(transaction.getIdStr()).transactionStatus(transaction.getStatus()).info(encryptedParams).build());
        } catch (Exception e) {
            final PaymentErrorType errorType = PAY015;
            builder.error(TechnicalErrorDetails.builder().code(errorType.getErrorCode()).description(errorType.getErrorMessage()).build()).status(errorType.getHttpResponseStatusCode()).success(false);
            log.error(errorType.getMarker(), e.getMessage(), e);
        }
        return builder.build();
    }

    @Override
    public WynkResponseEntity<Void> doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
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
            AnalyticService.update(PAYMENT_MODE, mode);
            boolean isUpi = StringUtils.isNotEmpty(mode) && mode.equals("UPI");
            // TODO:: Remove it once migration is completed
            String transactionId = StringUtils.isNotEmpty(payUChargingTransactionDetails.getMigratedTransactionId()) ? payUChargingTransactionDetails.getMigratedTransactionId() : paymentRenewalChargingRequest.getId();
            if (!isUpi || validateStatusForRenewal(merchantTransaction.getExternalTransactionId(), transaction)) {
                payURenewalResponse = doChargingForRenewal(paymentRenewalChargingRequest, merchantTransaction.getExternalTransactionId());
                payUChargingTransactionDetails = payURenewalResponse.getTransactionDetails().get(transaction.getIdStr());
                int retryInterval = planPeriodDTO.getRetryInterval();
                if (payURenewalResponse.getStatus() == 1) {
                    if (SUCCESS.equalsIgnoreCase(payUChargingTransactionDetails.getStatus())) {
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
        return WynkResponseEntity.<Void>builder().build();
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        final ChargingStatusResponse statusResponse = fetchAndUpdateTransactionFromSource(transactionStatusRequest, transactionStatusRequest.getExtTxnId());
        return WynkResponseEntity.<AbstractChargingStatusResponse>builder().data(statusResponse).build();
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
            log.error(PAYU_REFUND_STATUS_VERIFICATION, "Refund Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY004);
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(PAYU_REFUND_STATUS_VERIFICATION, "Unknown Refund Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY003);
        }
        ChargingStatusResponseBuilder<?, ?> responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus())
                .tid(transaction.getIdStr()).planId(transaction.getPlanId());
        if (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != PaymentEvent.POINT_PURCHASE) {
            responseBuilder.validity(cachingService.validTillDate(transaction.getPlanId()));
        }
        return responseBuilder.build();
    }

    private void syncRefundTransactionFromSource(Transaction transaction, String refundRequestId) {
        Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            MultiValueMap<String, String> payURefundStatusRequest = this.buildPayUInfoRequest(transaction.getClientAlias(),PayUCommand.CHECK_ACTION_STATUS.getCode(), refundRequestId);
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
            log.error(PAYU_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
            throw new WynkRuntimeException(PaymentErrorType.PAY998, e);
        } finally {
            eventPublisher.publishEvent(merchantTransactionEventBuilder.build());
        }
    }

    private ChargingStatusResponse fetchChargingStatusFromPayUSource(Transaction transaction) {
        syncChargingTransactionFromSource(transaction);
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            log.error(PAYU_CHARGING_STATUS_VERIFICATION, "Transaction is still pending at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY004);
        } else if (transaction.getStatus() == TransactionStatus.UNKNOWN) {
            log.error(PAYU_CHARGING_STATUS_VERIFICATION, "Unknown Transaction status at payU end for uid {} and transactionId {}", transaction.getUid(), transaction.getId().toString());
            throw new WynkRuntimeException(PaymentErrorType.PAY003);
        }
        ChargingStatusResponseBuilder<?, ?> responseBuilder = ChargingStatusResponse.builder().transactionStatus(transaction.getStatus())
                .tid(transaction.getIdStr()).planId(transaction.getPlanId());
        if (transaction.getStatus() == TransactionStatus.SUCCESS && transaction.getType() != PaymentEvent.POINT_PURCHASE) {
            responseBuilder.validity(cachingService.validTillDate(transaction.getPlanId()));
        }
        return responseBuilder.build();
    }

    public void syncChargingTransactionFromSource(Transaction transaction) {
        final Builder merchantTransactionEventBuilder = MerchantTransactionEvent.builder(transaction.getIdStr());
        try {
            final MultiValueMap<String, String> payUChargingVerificationRequest = this.buildPayUInfoRequest(transaction.getClientAlias(),PayUCommand.VERIFY_PAYMENT.getCode(), transaction.getId().toString());
            merchantTransactionEventBuilder.request(payUChargingVerificationRequest);
            final PayUVerificationResponse<PayUChargingTransactionDetails> payUChargingVerificationResponse = this.getInfoFromPayU(payUChargingVerificationRequest, new TypeReference<PayUVerificationResponse<PayUChargingTransactionDetails>>() {
            });
            merchantTransactionEventBuilder.response(payUChargingVerificationResponse);
            final PayUChargingTransactionDetails payUChargingTransactionDetails = payUChargingVerificationResponse.getTransactionDetails(transaction.getId().toString());
            if (StringUtils.isNotEmpty(payUChargingTransactionDetails.getMode()))
                AnalyticService.update(PAYMENT_MODE, payUChargingTransactionDetails.getMode());
            if (StringUtils.isNotEmpty(payUChargingTransactionDetails.getBankCode()))
                AnalyticService.update(BANK_CODE, payUChargingTransactionDetails.getBankCode());
            if (StringUtils.isNotEmpty(payUChargingTransactionDetails.getCardType()))
                AnalyticService.update(PAYU_CARD_TYPE, payUChargingTransactionDetails.getCardType());
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
            log.error(PAYU_CHARGING_STATUS_VERIFICATION, "unable to execute fetchAndUpdateTransactionFromSource due to ", e);
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
            if (SUCCESS.equalsIgnoreCase(transactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.SUCCESS;
            } else if (FAILURE.equalsIgnoreCase(transactionDetails.getStatus()) || (FAILED.equalsIgnoreCase(transactionDetails.getStatus())) || PAYU_STATUS_NOT_FOUND.equalsIgnoreCase(transactionDetails.getStatus())) {
                finalTransactionStatus = TransactionStatus.FAILURE;
            } else if ((transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - (ONE_DAY_IN_MILLI * retryInterval)) &&
                    (StringUtils.equalsIgnoreCase(PENDING, transactionDetails.getStatus()) || (transaction.getType() == PaymentEvent.REFUND && StringUtils.equalsIgnoreCase(QUEUED, transactionDetails.getStatus())))) {
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

    private Map<String, String> getPayload(PayUChargingRequest<?> chargingRequest) {
        final Transaction transaction = TransactionContext.get();
        final int planId = transaction.getPlanId();
        final PlanDTO selectedPlan = cachingService.getPlan(planId);
        double finalPlanAmount = transaction.getAmount();
        String uid = transaction.getUid();
        String msisdn = transaction.getMsisdn();
        final String email = uid + BASE_USER_EMAIL;
        final String payUMerchantKey = PropertyResolverUtils.resolve(transaction.getClientAlias(),transaction.getPaymentChannel().getCode().toLowerCase(),MERCHANT_ID);
        String userCredentials = payUMerchantKey + COLON + uid;
        Map<String, String> payloadTemp;
        if (transaction.getType() == PaymentEvent.SUBSCRIBE || transaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
            payloadTemp = getPayload(transaction.getClientAlias(),transaction.getId(), email, uid, planId, finalPlanAmount);
//            payloadTemp = getPayload(transaction.getId(), email, uid, planId, finalPlanAmount, selectedPlan, transaction.getType());
        } else {
            payloadTemp = getPayload(transaction.getClientAlias(),transaction.getId(), email, uid, planId, finalPlanAmount);
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

    private Map<String, String> getPayload(String client,UUID transactionId, String email, String uid, int planId, double finalPlanAmount) {
        Map<String, String> payload = new HashMap<>();
        String udf1 = StringUtils.EMPTY;
        String reqType = PaymentRequestType.DEFAULT.name();
        String checksumHash = getChecksumHashForPayment(client,transactionId, udf1, email, uid, String.valueOf(planId), finalPlanAmount);
        payload.put(PAYU_HASH, checksumHash);
        payload.put(PAYU_REQUEST_TYPE, reqType);
        payload.put(PAYU_UDF1_PARAMETER, udf1);
        return payload;
    }

    /* Not used therefore commenting as of now
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
     */

    private BillingUtils getBillingUtils(PlanDTO selectedPlan, boolean isFreeTrial) {
        int validTillDays = Math.toIntExact(selectedPlan.getPeriod().getTimeUnit().toDays(selectedPlan.getPeriod().getValidity()));
        int freeTrialValidity = isFreeTrial ? cachingService.getPlan(selectedPlan.getLinkedFreePlanId()).getPeriod().getValidity() : validTillDays;
        return freeTrialValidity == validTillDays ? new BillingUtils(validTillDays) : new BillingUtils(1, BillingCycle.ADHOC);
    }

    //TODO: ( on AMAN) need to use to fetch user's saved cards.
    /*public List<String> getUserCards(String uid) {
        String userCredentials = payUMerchantKey + COLON + uid;
        MultiValueMap<String, String> userCardDetailsRequest = buildPayUInfoRequest(PayUCommand.USER_CARD_DETAILS.getCode(), userCredentials);
        PayUUserCardDetailsResponse userCardDetailsResponse = getInfoFromPayU(userCardDetailsRequest, new TypeReference<PayUUserCardDetailsResponse>() {
        });
        return userCardDetailsResponse.getUserCards()
                .entrySet()
                .parallelStream()
                .map(cardEntry -> {
                    CardDetails cardDetails = cardEntry.getValue();
                    PayUBinWrapper<PayUCardInfo> payUBinWrapper = getInfoFromPayU(buildPayUInfoRequest(PayUCommand.CARD_BIN_INFO.getCode(), "1", new String[]{cardDetails.getCardBin(), null, null, "1"}),
                            new TypeReference<PayUBinWrapper<PayUCardInfo>>() {
                            });
                    return gson.toJson(cardDetails);
                })
                .collect(Collectors.toList());
    }
     */

    private boolean validateStatusForRenewal(String mihpayid, Transaction transaction) {
        LinkedHashMap<String, Object> orderedMap = new LinkedHashMap<>();
        orderedMap.put(PAYU_RESPONSE_AUTH_PAYUID, mihpayid);
        orderedMap.put(PAYU_REQUEST_ID, transaction.getIdStr());
        String variable = gson.toJson(orderedMap);
        PayUMandateUpiStatusResponse paymentResponse;
        rateLimiter.acquire();
        final MultiValueMap<String, String> requestMap = buildPayUInfoRequest(transaction.getClientAlias(),PayUCommand.UPI_MANDATE_STATUS.getCode(), variable);
        try {
            paymentResponse = getInfoFromPayU(requestMap, new TypeReference<PayUMandateUpiStatusResponse>() {
            });
        } catch (RestClientException e) {
            if (e.getRootCause() != null) {
                if (e.getRootCause() instanceof SocketTimeoutException || e.getRootCause() instanceof ConnectTimeoutException) {
                    log.error(PAYU_RENEWAL_STATUS_ERROR, "Socket timeout but valid for reconciliation for request : {} due to {}", requestMap, e.getMessage(), e);
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
        orderedMap.put(PAYU_INVOICE_DISPLAY_NUMBER, paymentRenewalChargingRequest.getId());
        orderedMap.put(PAYU_TRANSACTION_AMOUNT, amount);
        orderedMap.put(PAYU_REQUEST_TRANSACTION_ID, transaction.getIdStr());
        orderedMap.put(PAYU_CUSTOMER_MSISDN, msisdn);
        orderedMap.put(PAYU_CUSTOMER_EMAIL, email);
        String variable = gson.toJson(orderedMap);
        MultiValueMap<String, String> requestMap = buildPayUInfoRequest(transaction.getClientAlias(),PayUCommand.SI_TRANSACTION.getCode(), variable);
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
                    log.error(PAYU_RENEWAL_STATUS_ERROR, "Socket timeout but valid for reconciliation for request : {} due to {}", requestMap, e.getMessage(), e);
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

    private String initIntentUpiPayU(Map<String, String> payUPayload) {
        try {
            MultiValueMap<String, String> requestMap = new LinkedMultiValueMap<>();
            for (String key : payUPayload.keySet()) {
                requestMap.add(key, payUPayload.get(key));
            }
            payUPayload.clear();
            requestMap.add(PAYU_PG, "UPI");
            requestMap.add(PAYU_TXN_S2S_FLOW, "4");
            requestMap.add(PAYU_BANKCODE, "INTENT");
            return restTemplate.exchange(RequestEntity.method(HttpMethod.POST, URI.create(payUPaymentApiUrl)).body(requestMap), PayUUpiIntentInitResponse.class).getBody().getDeepLink();
        } catch (Exception ex) {
            log.error(PAYU_API_FAILURE, ex.getMessage(), ex);
            throw new WynkRuntimeException(PAY015, ex);
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

    private MultiValueMap<String, String> buildPayUInfoRequest(String client,String command, String var1, String... vars) {
        final String payUMerchantKey = PropertyResolverUtils.resolve(client,PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_ID);
        final String payUMerchantSecret = PropertyResolverUtils.resolve(client,PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_SECRET);
        String hash = generateHashForPayUApi(payUMerchantKey,payUMerchantSecret,command, var1);
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

    private String generateHashForPayUApi(String payUMerchantKey, String payUSalt,String command, String var1) {
        String builder = payUMerchantKey + PIPE_SEPARATOR +
                command +
                PIPE_SEPARATOR +
                var1 +
                PIPE_SEPARATOR +
                payUSalt;
        return EncryptionUtils.generateSHA512Hash(builder);
    }

    private void handleCallbackInternal(PayUCallbackRequestPayload payUCallbackRequestPayload) {
        final Transaction transaction = TransactionContext.get();
        final String transactionId = transaction.getIdStr();
        try {
            final String errorCode = payUCallbackRequestPayload.getError();
            final String errorMessage = payUCallbackRequestPayload.getErrorMessage();
            final String payUMerchantKey = PropertyResolverUtils.resolve(transaction.getClientAlias(),PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_ID);
            final String payUMerchantSecret = PropertyResolverUtils.resolve(transaction.getClientAlias(),PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_SECRET);
            final boolean isValidHash = validateCallbackChecksum(payUMerchantKey,payUMerchantSecret,transactionId,
                    payUCallbackRequestPayload.getStatus(),
                    payUCallbackRequestPayload.getUdf1(),
                    payUCallbackRequestPayload.getEmail(),
                    payUCallbackRequestPayload.getFirstName(),
                    String.valueOf(transaction.getPlanId()),
                    transaction.getAmount(),
                    payUCallbackRequestPayload.getResponseHash());

            if (isValidHash) {
                syncChargingTransactionFromSource(transaction);
            } else {
                log.error(PAYU_CHARGING_CALLBACK_FAILURE,
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

    /*
    Below function not used anywhere
     */
    /*
    private String getChecksumHashForPayment(UUID transactionId, String udf1, String email, String firstName, String planTitle, double amount, String siDetails) {
        String rawChecksum = payUMerchantKey + PIPE_SEPARATOR + transactionId.toString() + PIPE_SEPARATOR + amount + PIPE_SEPARATOR + planTitle +
                PIPE_SEPARATOR + firstName + PIPE_SEPARATOR + email + PIPE_SEPARATOR + udf1 + "||||||||||" + siDetails + PIPE_SEPARATOR + payUSalt;
        return EncryptionUtils.generateSHA512Hash(rawChecksum);
    }*/

    private String getChecksumHashForPayment(String client,UUID transactionId, String udf1, String email, String firstName, String planTitle, double amount) {
        final String payUMerchantKey = PropertyResolverUtils.resolve(client,PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_ID);
        final String payUMerchantSecret = PropertyResolverUtils.resolve(client,PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_SECRET);
        String rawChecksum = payUMerchantKey
                + PIPE_SEPARATOR + transactionId.toString() + PIPE_SEPARATOR + amount + PIPE_SEPARATOR + planTitle
                + PIPE_SEPARATOR + firstName + PIPE_SEPARATOR + email + PIPE_SEPARATOR + udf1 + "||||||||||" + payUMerchantSecret;
        return EncryptionUtils.generateSHA512Hash(rawChecksum);
    }

    private boolean validateCallbackChecksum(String payUMerchantKey,String payUSalt,String transactionId, String transactionStatus, String udf1, String email, String firstName, String planTitle, double amount, String payUResponseHash) {
        DecimalFormat df = new DecimalFormat("#.00");
        String generatedString =
                payUSalt + PIPE_SEPARATOR + transactionStatus + "||||||||||" + udf1 + PIPE_SEPARATOR + email + PIPE_SEPARATOR
                        + firstName + PIPE_SEPARATOR + planTitle + PIPE_SEPARATOR + df.format(amount) + PIPE_SEPARATOR + transactionId
                        + PIPE_SEPARATOR + payUMerchantKey;
        final String generatedHash = EncryptionUtils.generateSHA512Hash(generatedString);
        assert generatedHash != null;
        return generatedHash.equals(payUResponseHash);
    }

    @Override
    public BaseResponse<?> doVerify(VerificationRequest verificationRequest) {
        VerificationType verificationType = verificationRequest.getVerificationType();
        switch (verificationType) {
            case VPA:
                MultiValueMap<String, String> verifyVpaRequest = buildPayUInfoRequest(verificationRequest.getClient(),PayUCommand.VERIFY_VPA.getCode(), verificationRequest.getVerifyValue());
                PayUVpaVerificationResponse verificationResponse = getInfoFromPayU(verifyVpaRequest, new TypeReference<PayUVpaVerificationResponse>() {
                });
                if (verificationResponse.getIsVPAValid() == 1)
                    verificationResponse.setValid(true);
                return BaseResponse.<PayUVpaVerificationResponse>builder().body(verificationResponse).status(HttpStatus.OK).build();
            case BIN:
                MultiValueMap<String, String> verifyBinRequest = buildPayUInfoRequest(verificationRequest.getClient(),PayUCommand.CARD_BIN_INFO.getCode(), "1", new String[]{verificationRequest.getVerifyValue(), null, null, "1"});
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
                return BaseResponse.<PayUCardInfo>builder().body(cardInfo).status(cardInfo.isValid() ? HttpStatus.OK : HttpStatus.BAD_REQUEST).build();
        }
        return BaseResponse.status(false);
    }

    @Override
    public MerchantTransaction getMerchantTransactionDetails(Map<String, String> params) {
        MerchantTransaction.MerchantTransactionBuilder builder = MerchantTransaction.builder().id(params.get(TXN_ID));
        final String tid = params.containsKey(MIGRATED) && Boolean.parseBoolean(params.get(MIGRATED)) ? params.get(MIGRATED_TXN_ID) : params.get(TXN_ID);
        try {
            MultiValueMap<String, String> payUChargingVerificationRequest = this.buildPayUInfoRequest(TransactionContext.get().getClientAlias(),PayUCommand.VERIFY_PAYMENT.getCode(), tid);
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

    @Override
    public WynkResponseEntity<UserCardDetails> getUserPreferredPayments(PreferredPaymentDetailsRequest request) {
        WynkResponseEntity.WynkResponseEntityBuilder<UserCardDetails> builder = WynkResponseEntity.builder();
        final String payUMerchantKey = PropertyResolverUtils.resolve(request.getClientAlias(),PAYU_MERCHANT_PAYMENT_SERVICE.toLowerCase(),MERCHANT_ID);
        String userCredentials = payUMerchantKey + COLON + request.getPreferredPayment().getId().getUid();
        MultiValueMap<String, String> userCardDetailsRequest = buildPayUInfoRequest(request.getClientAlias(),PayUCommand.USER_CARD_DETAILS.getCode(), userCredentials);
        PayUUserCardDetailsResponse userCardDetailsResponse = getInfoFromPayU(userCardDetailsRequest, new TypeReference<PayUUserCardDetailsResponse>() {
        });
        Map<String, CardDetails> cardDetailsMap = userCardDetailsResponse.getUserCards();
        if (cardDetailsMap.isEmpty()) {
            builder.error(TechnicalErrorDetails.builder().code(PAY203.getErrorCode()).description(PAY203.getErrorMessage()).build()).success(false);
        }
        return builder.data(UserCardDetails.builder().cards(cardDetailsMap).build()).build();
    }

    @Override
    public WynkResponseEntity<PayUPaymentRefundResponse> refund(PayUPaymentRefundRequest refundRequest) {
        Transaction refundTransaction = TransactionContext.get();
        TransactionStatus finalTransactionStatus = TransactionStatus.INPROGRESS;
        Builder merchantTransactionBuilder = MerchantTransactionEvent.builder(refundTransaction.getIdStr());
        WynkResponseEntity.WynkResponseEntityBuilder<PayUPaymentRefundResponse> responseBuilder = WynkResponseEntity.builder();
        PayUPaymentRefundResponse.PayUPaymentRefundResponseBuilder<?, ?> refundResponseBuilder = PayUPaymentRefundResponse.builder().transactionId(refundTransaction.getIdStr()).uid(refundTransaction.getUid()).planId(refundTransaction.getPlanId()).itemId(refundTransaction.getItemId()).clientAlias(refundTransaction.getClientAlias()).amount(refundTransaction.getAmount()).msisdn(refundTransaction.getMsisdn()).paymentEvent(refundTransaction.getType());
        try {
            MultiValueMap<String, String> refundDetails = buildPayUInfoRequest(refundTransaction.getClientAlias(),PayUCommand.CANCEL_REFUND_TRANSACTION.getCode(), refundRequest.getAuthPayUId(), refundTransaction.getIdStr(), String.valueOf(refundTransaction.getAmount()));
            merchantTransactionBuilder.request(refundDetails);
            PayURefundInitResponse refundResponse = getInfoFromPayU(refundDetails, new TypeReference<PayURefundInitResponse>() {
            });
            if (refundResponse.getStatus() == 0) {
                finalTransactionStatus = TransactionStatus.FAILURE;
                PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(String.valueOf(refundResponse.getStatus())).description(refundResponse.getMessage()).build();
                responseBuilder.success(false).error(StandardBusinessErrorDetails.builder().code(errorEvent.getCode()).description(errorEvent.getDescription()).build());
                eventPublisher.publishEvent(errorEvent);
            } else {
                refundResponseBuilder.authPayUId(refundResponse.getAuthPayUId()).requestId(refundResponse.getRequestId());
                merchantTransactionBuilder.externalTransactionId(refundResponse.getRequestId()).response(refundResponse).build();
            }
        } catch (WynkRuntimeException ex) {
            PaymentErrorEvent errorEvent = PaymentErrorEvent.builder(refundTransaction.getIdStr()).code(ex.getErrorCode()).description(ex.getErrorTitle()).build();
            responseBuilder.success(false).status(ex.getErrorType().getHttpResponseStatusCode()).error(TechnicalErrorDetails.builder().code(errorEvent.getCode()).description(errorEvent.getDescription()).build());
            eventPublisher.publishEvent(errorEvent);
        } finally {
            refundTransaction.setStatus(finalTransactionStatus.getValue());
            refundResponseBuilder.transactionStatus(finalTransactionStatus);
            eventPublisher.publishEvent(merchantTransactionBuilder.build());
        }
        return responseBuilder.data(refundResponseBuilder.build()).build();
    }

    @Override
    public void sendPreDebitNotification(PreDebitNotificationRequest request) {
        try {
            LinkedHashMap<String, Object> orderedMap = new LinkedHashMap<>();
            MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(request.getTransactionId());
            orderedMap.put(PAYU_RESPONSE_AUTH_PAYUID, merchantTransaction.getExternalTransactionId());
            orderedMap.put(PAYU_REQUEST_ID, UUIDs.timeBased());
            orderedMap.put(PAYU_DEBIT_DATE, request.getDate());
            orderedMap.put(PAYU_INVOICE_DISPLAY_NUMBER, request.getTransactionId());
            orderedMap.put(PAYU_TRANSACTION_AMOUNT, cachingService.getPlan(request.getPlanId()).getFinalPrice());
            String variable = gson.toJson(orderedMap);
            ITransactionManagerService transactionManagerService = BeanLocatorFactory.getBean(ITransactionManagerService.class);
            MultiValueMap<String, String> requestMap = buildPayUInfoRequest(transactionManagerService.get(request.getTransactionId()).getClientAlias(),PayUCommand.PRE_DEBIT_SI.getCode(), variable);
            PayUPreDebitNotificationResponse response = this.getInfoFromPayU(requestMap, new TypeReference<PayUPreDebitNotificationResponse>() {
            });
            AnalyticService.update(PRE_DEBIT_SI.getCode(), gson.toJson(response));
            if (response.getStatus() == 1) {
                log.info(PAYU_PRE_DEBIT_NOTIFICATION_SUCCESS, "invoiceId: " + response.getInvoiceId() + " invoiceStatus: " + response.getInvoiceStatus());
            } else {
                log.error(PAYU_PRE_DEBIT_NOTIFICATION_ERROR, response.getMessage());
                throw new WynkRuntimeException(PAY111);
            }
        } catch (Exception e) {
            log.error(PAYU_PRE_DEBIT_NOTIFICATION_ERROR, e.getMessage());
            throw new WynkRuntimeException(PAY111);
        }
    }

    @Override
    public void cancelRecurring(String transactionId) {
        try {
            LinkedHashMap<String, String> orderedMap = new LinkedHashMap<>();
            MerchantTransaction merchantTransaction = merchantTransactionService.getMerchantTransaction(transactionId);
            orderedMap.put(PAYU_RESPONSE_AUTH_PAYUID, merchantTransaction.getExternalTransactionId());
            orderedMap.put(PAYU_REQUEST_ID, transactionId);
            String variable = gson.toJson(orderedMap);
            ITransactionManagerService transactionManagerService = BeanLocatorFactory.getBean(ITransactionManagerService.class);
            MultiValueMap<String, String> requestMap = buildPayUInfoRequest(transactionManagerService.get(transactionId).getClientAlias(),UPI_MANDATE_REVOKE.getCode(), variable);
            PayUBaseResponse response = this.getInfoFromPayU(requestMap, new TypeReference<PayUBaseResponse>() {
            });
            AnalyticService.update(UPI_MANDATE_REVOKE.getCode(), gson.toJson(response));
        } catch (Exception e) {
            log.error(PAYU_UPI_MANDATE_REVOKE_ERROR, e.getMessage());
            throw new WynkRuntimeException(PAY112);
        }
    }

}