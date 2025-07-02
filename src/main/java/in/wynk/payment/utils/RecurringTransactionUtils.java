package in.wynk.payment.utils;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.client.data.utils.RepositoryUtils;
import in.wynk.common.dto.WynkResponse;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.PaymentRenewalDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dao.repository.PaymentRenewalDetailsDao;
import in.wynk.payment.core.event.MandateStatusEvent;
import in.wynk.payment.core.event.UnScheduleRecurringPaymentEvent;
import in.wynk.payment.dto.request.AbstractUnSubscribePlanRequest;
import in.wynk.payment.dto.request.AsyncTransactionRevisionRequest;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.RenewalPlanEligibilityResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

import static in.wynk.common.enums.PaymentEvent.RENEW;
import static in.wynk.payment.core.constant.PaymentConstants.*;

/**
 * @author Nishesh Pandey
 */

@Component
@RequiredArgsConstructor
@Slf4j
public class RecurringTransactionUtils {

    private final IRecurringPaymentManagerService recurringPaymentManagerService;
    private final ApplicationEventPublisher eventPublisher;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final ITransactionManagerService transactionManagerService;
    private final PaymentCachingService cachingService;

    @Value("${payment.renewal.unsupported}")
    private List<String> renewalUnSupportedPG;
    @Value("${payment.recurring.offset.hour}")
    private int hour;

    public void cancelRenewalBasedOnErrorReason (String description, Transaction transaction) {
        if (ERROR_REASONS.contains(description)) {
            updateSubscriptionAndTransaction(description, transaction, false, PaymentEvent.UNSUBSCRIBE);
        }
    }

    @TransactionAware(txnId = "#txn.id")
    private void updateSubscriptionAndTransaction (String description, Transaction txn, boolean isRealTimeMandateFlow, PaymentEvent event) {
        Transaction transactionCopy = Transaction.builder().id(txn.getIdStr()).planId(txn.getPlanId()).amount(txn.getAmount()).mandateAmount(txn.getMandateAmount()).discount(txn.getDiscount())
                .initTime(txn.getInitTime()).uid(txn.getUid()).msisdn(txn.getMsisdn()).clientAlias(txn.getClientAlias()).itemId(txn.getItemId())
                .paymentChannel(txn.getPaymentChannel().getId()).type(txn.getType().getValue()).status(txn.getStatus().getValue()).coupon(txn.getCoupon()).exitTime(txn.getExitTime())
                .consent(txn.getConsent()).build();
        try {
            PaymentRenewal renewal = recurringPaymentManagerService.getRenewalById(transactionCopy.getIdStr());
            if (Objects.nonNull(renewal)) {
                final String referenceTransactionId = renewal.getInitialTransactionId();
                eventPublisher.publishEvent(UnScheduleRecurringPaymentEvent.builder().transactionId(transactionCopy.getIdStr()).clientAlias(transactionCopy.getClientAlias())
                        .reason("Stopping Payment Renewal because " + description).build());
                eventPublisher.publishEvent(MandateStatusEvent.builder().txnId(transactionCopy.getIdStr()).clientAlias(transactionCopy.getClientAlias()).errorReason(description)
                        .referenceTransactionId(referenceTransactionId).planId(transactionCopy.getPlanId()).paymentMethod(transactionCopy.getPaymentChannel().getCode()).uid(transactionCopy.getUid())
                        .build());
                if (isRealTimeMandateFlow) {
                    Integer planId;
                    AsyncTransactionRevisionRequest request;
                    if (EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION, PaymentEvent.MANDATE, PaymentEvent.FREE).contains(transactionCopy.getType())) {
                        planId = subscriptionServiceManager.getUpdatedPlanId(transactionCopy.getPlanId(), transactionCopy.getType());
                        transactionCopy.setPlanId(planId);
                        transactionCopy.setType(PaymentEvent.UNSUBSCRIBE.getValue());
                        request = AsyncTransactionRevisionRequest.builder().transaction(transactionCopy).existingTransactionStatus(transactionCopy.getStatus())
                                .finalTransactionStatus(TransactionStatus.CANCELLED).build();
                    } else if (transactionCopy.getType() == PaymentEvent.RENEW && transactionCopy.getStatus() == TransactionStatus.FAILURE) {
                        String lastSuccessTxnId = renewal.getLastSuccessTransactionId();
                        Transaction lastSuccessTransaction = transactionManagerService.get(lastSuccessTxnId);
                        if (lastSuccessTransaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
                            planId = subscriptionServiceManager.getUpdatedPlanId(lastSuccessTransaction.getPlanId(), lastSuccessTransaction.getType());
                            lastSuccessTransaction.setPlanId(planId);
                        }
                        lastSuccessTransaction.setType(PaymentEvent.UNSUBSCRIBE.getValue());
                        request = AsyncTransactionRevisionRequest.builder().transaction(lastSuccessTransaction).existingTransactionStatus(lastSuccessTransaction.getStatus())
                                .finalTransactionStatus(TransactionStatus.CANCELLED).build();
                    } else {
                        if (event == PaymentEvent.UNSUBSCRIBE) {
                            transactionCopy.setType(PaymentEvent.UNSUBSCRIBE.getValue());
                            request = AsyncTransactionRevisionRequest.builder().transaction(transactionCopy).existingTransactionStatus(transactionCopy.getStatus())
                                    .finalTransactionStatus(TransactionStatus.CANCELLED).build();
                        } else {
                            transactionCopy.setType(PaymentEvent.CANCELLED.getValue());
                            request = AsyncTransactionRevisionRequest.builder().transaction(transactionCopy).existingTransactionStatus(transactionCopy.getStatus())
                                    .finalTransactionStatus(TransactionStatus.CANCELLED).build();
                        }
                    }
                    subscriptionServiceManager.unSubscribePlan(AbstractUnSubscribePlanRequest.from(request));
                    if ((request.getTransaction().getPaymentChannel().getId().equals(AMAZON_IAP) || request.getTransaction().getPaymentChannel().getId().equals(GOOGLE_IAP) || request.getTransaction().getPaymentChannel().getId().equals(PaymentConstants.ITUNES)) && event == PaymentEvent.CANCELLED) {
                        request.getTransaction().setType(txn.getType().toString());
                        updateTransaction(request.getTransaction());
                    } else {
                        updateTransaction(request.getTransaction());
                    }
                }
            }
        } catch (Exception e) {
            log.error("Unable to update renewal table for cancellation and mandate status event could not be generated", e);
        }

    }

    private void updateTransaction (Transaction transaction) {
        transaction.setStatus(TransactionStatus.CANCELLED.getValue());
        transactionManagerService.upsert(transaction);
    }

    public void cancelRenewalBasedOnRealtimeMandate (String description, Transaction firstTransaction) {
        try {
            PaymentRenewal paymentRenewal = recurringPaymentManagerService.getLatestRecurringPaymentByInitialTxnId(firstTransaction.getIdStr());
            if (Objects.nonNull(paymentRenewal)) {
                final Transaction transaction =
                        (firstTransaction.getIdStr().equals(paymentRenewal.getTransactionId())) ? firstTransaction : transactionManagerService.get(paymentRenewal.getTransactionId());
                updateSubscriptionAndTransaction(description, transaction, true, PaymentEvent.UNSUBSCRIBE);
            } else {
                eventPublisher.publishEvent(MandateStatusEvent.builder().txnId(firstTransaction.getIdStr()).clientAlias(firstTransaction.getClientAlias()).errorReason(description)
                        .referenceTransactionId(null).planId(firstTransaction.getPlanId()).paymentMethod(firstTransaction.getPaymentChannel().getCode()).uid(firstTransaction.getUid())
                        .build());
                log.error(PaymentLoggingMarker.STOP_RENEWAL_FAILURE, PaymentErrorType.RTMANDATE001.getErrorMessage());
            }
        } catch (Exception ex) {
            throw new WynkRuntimeException(PaymentErrorType.RTMANDATE001);
        }
    }

    public boolean checkRenewalEligibility (String transactionId, int attemptSequence) {
        Transaction transaction = transactionManagerService.get(transactionId);
        if ((transaction.getStatus() == TransactionStatus.FAILURE && attemptSequence >= PaymentConstants.MAXIMUM_RENEWAL_RETRY) ||
                (transaction.getStatus() == TransactionStatus.FAILURE && transaction.getType() != RENEW) || (transaction.getStatus() == TransactionStatus.CANCELLED)) {
            try {
                eventPublisher.publishEvent(UnScheduleRecurringPaymentEvent.builder().transactionId(transaction.getIdStr()).clientAlias(transaction.getClientAlias())
                        .reason("Stopping Payment Renewal because transaction status is " + transaction.getStatus().getValue()).build());
            } catch (Exception e) {
                return false;
            }
            return false;
        }
        return !renewalUnSupportedPG.contains(transaction.getPaymentChannel().getId());
    }

    public boolean isEligibleForRenewal (Transaction transaction, boolean isPreDebitFlow) {
        if (!isPlanDeprecated(transaction)) {
            ResponseEntity<WynkResponse.WynkResponseWrapper<RenewalPlanEligibilityResponse>> response =
                    subscriptionServiceManager.renewalPlanEligibilityResponse(transaction.getPlanId(), transaction.getUid());
            if (Objects.nonNull(response.getBody()) && Objects.nonNull(response.getBody().getData())) {
                RenewalPlanEligibilityResponse renewalPlanEligibilityResponse = response.getBody().getData();
                long today = System.currentTimeMillis();
                long furtherDefer = renewalPlanEligibilityResponse.getDeferredUntil() - today;
                if (subscriptionServiceManager.isDeferred(transaction.getPaymentChannel().getCode(), furtherDefer, isPreDebitFlow)) {
                    if (Objects.equals(transaction.getPaymentChannel().getCode(), BeanConstant.AIRTEL_PAY_STACK)) {
                        furtherDefer = furtherDefer - ((long) 2 * 24 * 60 * 60 * 1000);
                    }
                    recurringPaymentManagerService.unScheduleRecurringPayment(transaction, PaymentEvent.DEFERRED, today, furtherDefer);
                    return false;
                }
            }
            return true;
        }
        return false;
    }

    private boolean isPlanDeprecated (Transaction transaction) {
        try {
            final PlanDTO planDTO = cachingService.getPlan(transaction.getPlanId());
            Optional<PaymentRenewalDetails>
                    mapping = RepositoryUtils.getRepositoryForClient(ClientContext.getClient().map(Client::getAlias).orElse(PaymentConstants.PAYMENT_API_CLIENT), PaymentRenewalDetailsDao.class).findById(planDTO.getService());
            if (mapping.isPresent() && mapping.get().get(PaymentConstants.RENEWALS_INELIGIBLE_PLANS).isPresent()) {
                final List<Integer> renewalsDeprecatedPlans = (List<Integer>) mapping.get().getMeta().get(PaymentConstants.RENEWALS_INELIGIBLE_PLANS);
                if (renewalsDeprecatedPlans.contains(transaction.getPlanId())) {
                    return true;
                }
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    public void cancelRenewalBasedOnRealtimeMandateForIAP (String description, Transaction transaction, PaymentEvent event) {
        try {
            updateSubscriptionAndTransaction(description, transaction, true, event);
        } catch (Exception ex) {
            throw new WynkRuntimeException(PaymentErrorType.RTMANDATE002, ex);
        }
    }
}
