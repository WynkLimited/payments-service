package in.wynk.payment.utils;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MandateStatusEvent;
import in.wynk.payment.core.event.UnScheduleRecurringPaymentEvent;
import in.wynk.payment.dto.request.AbstractUnSubscribePlanRequest;
import in.wynk.payment.dto.request.AsyncTransactionRevisionRequest;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.ITransactionManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.util.EnumSet;
import java.util.Objects;

import static in.wynk.payment.core.constant.PaymentConstants.ERROR_REASONS;

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

    public void cancelRenewalBasedOnErrorReason (String description, Transaction transaction) {
        if (ERROR_REASONS.contains(description)) {
            updateSubscriptionAndTransaction(description, transaction);
        }
    }

    @TransactionAware(txnId = "#transaction.id")
    private void updateSubscriptionAndTransaction (String description, Transaction transaction) {
        try {
            PaymentRenewal renewal = recurringPaymentManagerService.getRenewalById(transaction.getIdStr());
            if (Objects.nonNull(renewal)) {
                final String referenceTransactionId = renewal.getInitialTransactionId();
                eventPublisher.publishEvent(UnScheduleRecurringPaymentEvent.builder().transactionId(transaction.getIdStr()).clientAlias(transaction.getClientAlias())
                        .reason("Stopping Payment Renewal because " + description).build());
                eventPublisher.publishEvent(MandateStatusEvent.builder().txnId(transaction.getIdStr()).clientAlias(transaction.getClientAlias()).errorReason(description)
                        .referenceTransactionId(referenceTransactionId).planId(transaction.getPlanId()).paymentMethod(transaction.getPaymentChannel().getCode()).uid(transaction.getUid()).build());
                Integer planId;
                AsyncTransactionRevisionRequest request;
                if (EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION, PaymentEvent.MANDATE, PaymentEvent.FREE).contains(transaction.getType())) {
                    planId = subscriptionServiceManager.getUpdatedPlanId(transaction.getPlanId(), transaction.getType());
                    transaction.setPlanId(planId);
                    transaction.setType(PaymentEvent.UNSUBSCRIBE.getValue());
                    request = AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(transaction.getStatus())
                            .finalTransactionStatus(TransactionStatus.CANCELLED).build();
                } else if (transaction.getType() == PaymentEvent.RENEW && transaction.getStatus() == TransactionStatus.FAILURE) {
                    String lastSuccessTxnId = renewal.getLastSuccessTransactionId();
                    Transaction lastSuccessTransaction = transactionManagerService.get(lastSuccessTxnId);
                    lastSuccessTransaction.setType(PaymentEvent.UNSUBSCRIBE.getValue());
                    if (lastSuccessTransaction.getType() == PaymentEvent.TRIAL_SUBSCRIPTION) {
                        planId = subscriptionServiceManager.getUpdatedPlanId(lastSuccessTransaction.getPlanId(), lastSuccessTransaction.getType());
                        lastSuccessTransaction.setPlanId(planId);
                    }
                    request = AsyncTransactionRevisionRequest.builder().transaction(lastSuccessTransaction).existingTransactionStatus(lastSuccessTransaction.getStatus())
                            .finalTransactionStatus(TransactionStatus.CANCELLED).build();
                } else {
                    transaction.setType(PaymentEvent.UNSUBSCRIBE.getValue());
                    request = AsyncTransactionRevisionRequest.builder().transaction(transaction).existingTransactionStatus(transaction.getStatus())
                            .finalTransactionStatus(TransactionStatus.CANCELLED).build();
                }
                subscriptionServiceManager.unSubscribePlan(AbstractUnSubscribePlanRequest.from(request));
            }
        } catch (Exception e) {
            log.error("Unable to update renewal table for cancellation and mandate status event could not be generated", e);
        }

    }

    //find last renewal txn id and update the same
    public void cancelRenewalBasedOnRealtimeMandate (String description, Transaction firstTransaction) {
        PaymentRenewal paymentRenewal = recurringPaymentManagerService.getLatestRecurringPaymentByInitialTxnId(firstTransaction.getIdStr());
        if (Objects.nonNull(paymentRenewal)) {
            final Transaction transaction = (firstTransaction.getIdStr().equals(paymentRenewal.getTransactionId())) ? firstTransaction : transactionManagerService.get(paymentRenewal.getTransactionId());
            updateSubscriptionAndTransaction(description, transaction);
        } else {
            throw new WynkRuntimeException(PaymentErrorType.RTMANDATE001);
        }
    }
}