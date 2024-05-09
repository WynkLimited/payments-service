package in.wynk.payment.utils;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.MandateStatusEvent;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

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

    public void cancelRenewalBasedOnErrorReason (String description, Transaction transaction) {
        if (ERROR_REASONS.contains(description)) {
            try {
                String referenceTransactionId = transaction.getIdStr();
                if (transaction.getType() == PaymentEvent.RENEW) {
                    PaymentRenewal renewal = recurringPaymentManagerService.getRenewalById(transaction.getIdStr());
                    if (Objects.nonNull(renewal)) {
                        if (StringUtils.isNotBlank(renewal.getLastSuccessTransactionId())) {
                            referenceTransactionId = renewal.getLastSuccessTransactionId();
                        }
                    }
                }
                recurringPaymentManagerService.unScheduleRecurringPayment(transaction.getClientAlias(), transaction.getIdStr(), PaymentEvent.CANCELLED);
                eventPublisher.publishEvent(
                        MandateStatusEvent.builder().errorReason(description).clientAlias(transaction.getClientAlias()).txnId(transaction.getIdStr()).referenceTransactionId(referenceTransactionId)
                                .uid(transaction.getUid()).paymentMethod(transaction.getPaymentChannel().getCode()).planId(transaction.getPlanId()).build());
            } catch (Exception e) {
                log.error("Unable to update renewal table for cancellation and mandate status event could not be generated", e);
            }
        }
    }
}