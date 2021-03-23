package in.wynk.payment.service;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;

import java.util.stream.Stream;

public interface IRecurringPaymentManagerService {

    void scheduleRecurringPayment(Transaction transaction, TransactionStatus existingTransactionStatus, TransactionStatus finalTransactionStatus);

    Stream<PaymentRenewal> getCurrentDueRecurringPayments();

    void unScheduleRecurringPayment(String transactionId, PaymentEvent paymentEvent, long validUntil, long deferredUntil);
}