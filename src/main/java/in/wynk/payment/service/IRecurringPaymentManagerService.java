package in.wynk.payment.service;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;

import java.util.stream.Stream;

public interface IRecurringPaymentManagerService {

    void schedulePaymentRenewal(Transaction transaction);

    void schedulePaymentRenewalForFreeTrial(Transaction transaction);

    void schedulePaymentRenewalForMigration(Transaction transaction);

    void schedulePaymentRenewalForNextRetry(Transaction transaction);

    Stream<PaymentRenewal> getCurrentDueRecurringPayments();

    void unScheduleRecurringPayment(String transactionId, PaymentEvent paymentEvent, long validTillDate);
}