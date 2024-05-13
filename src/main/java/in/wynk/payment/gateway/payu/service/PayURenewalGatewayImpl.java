package in.wynk.payment.gateway.payu.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import com.google.common.util.concurrent.RateLimiter;
import com.google.gson.Gson;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.MerchantTransaction;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.PaymentErrorEvent;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.payu.PayUChargingTransactionDetails;
import in.wynk.payment.dto.payu.PayUCommand;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.payu.PayURenewalResponse;
import in.wynk.payment.dto.response.payu.PayUVerificationResponse;
import in.wynk.payment.gateway.IPaymentRenewal;
import in.wynk.payment.service.IMerchantTransactionService;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.RecurringTransactionUtils;
import in.wynk.subscription.common.dto.PlanPeriodDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.conn.ConnectTimeoutException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClientException;

import java.net.SocketTimeoutException;
import java.util.LinkedHashMap;
import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.ONE_DAY_IN_MILLI;
import static in.wynk.payment.core.constant.PaymentConstants.*;
import static in.wynk.payment.core.constant.PaymentLoggingMarker.PAYU_RENEWAL_STATUS_ERROR;
import static in.wynk.payment.dto.payu.PayUConstants.*;

@Slf4j
public class PayURenewalGatewayImpl implements IPaymentRenewal<PaymentRenewalChargingRequest> {

    private final Gson gson;
    private final ObjectMapper objectMapper;
    private final PayUCommonGateway common;
    private final PaymentCachingService cachingService;
    private final ApplicationEventPublisher eventPublisher;
    private final RateLimiter rateLimiter = RateLimiter.create(6.0);
    private final IMerchantTransactionService merchantTransactionService;
    private final ITransactionManagerService transactionManager;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;
    private final RecurringTransactionUtils recurringTransactionUtils;

    public PayURenewalGatewayImpl (PayUCommonGateway common, Gson gson, ObjectMapper objectMapper, PaymentCachingService cachingService, ApplicationEventPublisher eventPublisher,
                                   IMerchantTransactionService merchantTransactionService, ITransactionManagerService transactionManager,
                                   IRecurringPaymentManagerService recurringPaymentManagerService, RecurringTransactionUtils recurringTransactionUtils) {
        this.gson = gson;
        this.common = common;
        this.objectMapper = objectMapper;
        this.cachingService = cachingService;
        this.eventPublisher = eventPublisher;
        this.merchantTransactionService = merchantTransactionService;
        this.transactionManager = transactionManager;
        this.recurringPaymentManagerService = recurringPaymentManagerService;
        this.recurringTransactionUtils = recurringTransactionUtils;
    }

    @Override
    public void renew (PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        Transaction transaction = TransactionContext.get();
        PlanPeriodDTO planPeriodDTO = cachingService.getPlan(transaction.getPlanId()).getPeriod();
        String txnId = paymentRenewalChargingRequest.getId();
        PaymentRenewal renewal = recurringPaymentManagerService.getRenewalById(txnId);
        if (Objects.nonNull(renewal) && StringUtils.isNotBlank(renewal.getLastSuccessTransactionId())) {
            txnId = renewal.getLastSuccessTransactionId();
        }
        MerchantTransaction merchantTransaction = getMerchantData(txnId);
        PayUVerificationResponse<PayUChargingTransactionDetails> currentStatus =
                (merchantTransaction == null) ? common.syncChargingTransactionFromSource(transactionManager.get(txnId)) : null;
        try {
            PayURenewalResponse payURenewalResponse = (merchantTransaction == null) ? objectMapper.convertValue(currentStatus, PayURenewalResponse.class) :
                    objectMapper.convertValue(merchantTransaction.getResponse(), PayURenewalResponse.class);
            PayUChargingTransactionDetails payUChargingTransactionDetails = payURenewalResponse.getTransactionDetails().get(txnId);
            String mode = payUChargingTransactionDetails.getMode();
            AnalyticService.update(PAYMENT_MODE, mode);
            boolean isUpi = StringUtils.isNotEmpty(mode) && mode.equals("UPI");
            String externalTransactionId =
                    (merchantTransaction != null) ? merchantTransaction.getExternalTransactionId() : currentStatus.getTransactionDetails(txnId).getPayUExternalTxnId();
            if (!isUpi || common.validateStatusForRenewal(externalTransactionId, transaction)) {
                payURenewalResponse = doChargingForRenewal(paymentRenewalChargingRequest, externalTransactionId, txnId);
                payUChargingTransactionDetails = payURenewalResponse.getTransactionDetails().get(transaction.getIdStr());
                int retryInterval = planPeriodDTO.getRetryInterval();
                if (payURenewalResponse.getStatus() == 1) {
                    if (SUCCESS.equalsIgnoreCase(payUChargingTransactionDetails.getStatus())) {
                        transaction.setStatus(TransactionStatus.SUCCESS.getValue());
                    } else if (FAILURE.equalsIgnoreCase(payUChargingTransactionDetails.getStatus()) || (FAILED.equalsIgnoreCase(payUChargingTransactionDetails.getStatus())) ||
                            PAYU_STATUS_NOT_FOUND.equalsIgnoreCase(payUChargingTransactionDetails.getStatus())) {
                        transaction.setStatus(TransactionStatus.FAILURE.getValue());
                        String errorReason = findPayuErrorMessage(payUChargingTransactionDetails);
                        recurringTransactionUtils.cancelRenewalBasedOnErrorReason(errorReason, transaction);
                        eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(payUChargingTransactionDetails.getErrorCode()).description(errorReason).build());
                    } else if (transaction.getInitTime().getTimeInMillis() > System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
                            StringUtils.equalsIgnoreCase(PENDING, payUChargingTransactionDetails.getStatus())) {
                        transaction.setStatus(TransactionStatus.INPROGRESS.getValue());
                    } else if (transaction.getInitTime().getTimeInMillis() < System.currentTimeMillis() - ONE_DAY_IN_MILLI * retryInterval &&
                            StringUtils.equalsIgnoreCase(PENDING, payUChargingTransactionDetails.getStatus())) {
                        transaction.setStatus(TransactionStatus.FAILURE.getValue());
                        eventPublisher.publishEvent(
                                PaymentErrorEvent.builder(transaction.getIdStr()).code(payUChargingTransactionDetails.getErrorCode()).description("Transaction init time is less than current - 1")
                                        .build());
                    }
                } else {
                    transaction.setStatus(TransactionStatus.FAILURE.getValue());
                    String errorReason = findPayuErrorMessage(payUChargingTransactionDetails);
                    recurringTransactionUtils.cancelRenewalBasedOnErrorReason(errorReason, transaction);
                    eventPublisher.publishEvent(PaymentErrorEvent.builder(transaction.getIdStr()).code(payUChargingTransactionDetails.getErrorCode()).description(errorReason).build());
                }
            }
        } catch (WynkRuntimeException e) {
            if (e.getErrorCode().equals(PaymentErrorType.PAY009.getErrorCode()) || e.getErrorCode().equals(PaymentErrorType.PAY002.getErrorCode())) {
                transaction.setStatus(TransactionStatus.FAILURE.getValue());
            }
            throw e;
        }
    }

    private String findPayuErrorMessage (PayUChargingTransactionDetails payUChargingTransactionDetails) {
        String errorReason = null;
        if (StringUtils.isNotBlank(payUChargingTransactionDetails.getMostSpecificFailureReason())) {
            errorReason = payUChargingTransactionDetails.getMostSpecificFailureReason();
        } else if (StringUtils.isNotBlank(payUChargingTransactionDetails.getSpecificFailureReason())) {
            errorReason = payUChargingTransactionDetails.getSpecificFailureReason();
        } else if (StringUtils.isNotBlank(payUChargingTransactionDetails.getPayUResponseFailureMessage())) {
            errorReason = payUChargingTransactionDetails.getPayUResponseFailureMessage();
        } else if (StringUtils.isNotBlank(payUChargingTransactionDetails.getErrorMessage())) {
            errorReason = payUChargingTransactionDetails.getErrorMessage();
        }
        AnalyticService.update(ERROR_REASON, errorReason);
        return errorReason;
    }

    private MerchantTransaction getMerchantData (String id) {
        try {
            return merchantTransactionService.getMerchantTransaction(id);
        } catch (Exception e) {
            log.error("Exception occurred while getting data for tid {} from merchant table: {}", id, e.getMessage());
            return null;
        }
    }

    private PayURenewalResponse doChargingForRenewal (PaymentRenewalChargingRequest paymentRenewalChargingRequest, String mihpayid, String lastSuccessTxnId) {
        Transaction transaction = TransactionContext.get();
        LinkedHashMap<String, Object> orderedMap = new LinkedHashMap<>();
        String uid = paymentRenewalChargingRequest.getUid();
        String msisdn = paymentRenewalChargingRequest.getMsisdn();
        double amount = cachingService.getPlan(transaction.getPlanId()).getFinalPrice();
        final String email = uid + BASE_USER_EMAIL;
        orderedMap.put(PAYU_RESPONSE_AUTH_PAYUID_SMALL, mihpayid);
        orderedMap.put(PAYU_INVOICE_DISPLAY_NUMBER, lastSuccessTxnId);
        orderedMap.put(PAYU_TRANSACTION_AMOUNT, amount);
        orderedMap.put(PAYU_REQUEST_TRANSACTION_ID, transaction.getIdStr());
        orderedMap.put(PAYU_CUSTOMER_MSISDN, msisdn);
        orderedMap.put(PAYU_CUSTOMER_EMAIL, email);
        String variable = gson.toJson(orderedMap);
        MultiValueMap<String, String> requestMap = common.buildPayUInfoRequest(transaction.getClientAlias(), PayUCommand.SI_TRANSACTION.getCode(), variable);
        rateLimiter.acquire();
        try {
            PayURenewalResponse paymentResponse = common.exchange(common.PAYMENT_API, requestMap, new TypeReference<PayURenewalResponse>() {
            });
            if (paymentResponse == null) {
                paymentResponse = new PayURenewalResponse();
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
        }
    }
}
