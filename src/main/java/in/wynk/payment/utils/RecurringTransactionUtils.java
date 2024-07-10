package in.wynk.payment.utils;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
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
            updateSubscriptionAndTransaction(description, transaction, false);
        }
    }

    @TransactionAware(txnId = "#txn.id")
    private void updateSubscriptionAndTransaction (String description, Transaction txn, boolean isRealTimeMandateFlow) {
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
                        transactionCopy.setType(PaymentEvent.UNSUBSCRIBE.getValue());
                        request = AsyncTransactionRevisionRequest.builder().transaction(transactionCopy).existingTransactionStatus(transactionCopy.getStatus())
                                .finalTransactionStatus(TransactionStatus.CANCELLED).build();
                    }

                    subscriptionServiceManager.unSubscribePlan(AbstractUnSubscribePlanRequest.from(request));
                }
            }
        } catch (Exception e) {
            log.error("Unable to update renewal table for cancellation and mandate status event could not be generated", e);
        }

    }

    public void cancelRenewalBasedOnRealtimeMandate (String description, Transaction firstTransaction) {
        try {
            PaymentRenewal paymentRenewal = recurringPaymentManagerService.getLatestRecurringPaymentByInitialTxnId(firstTransaction.getIdStr());
            if (Objects.nonNull(paymentRenewal)) {
                final Transaction transaction =
                        (firstTransaction.getIdStr().equals(paymentRenewal.getTransactionId())) ? firstTransaction : transactionManagerService.get(paymentRenewal.getTransactionId());
                updateSubscriptionAndTransaction(description, transaction, true);
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
}