package in.wynk.payment.service;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.AbstractTransactionRevisionRequest;

import java.util.Calendar;
import java.util.Date;
import java.util.Optional;
import java.util.stream.Stream;

public interface IRecurringPaymentManagerService {

    Stream<PaymentRenewal> getCurrentDueNotifications (String clientAlias);

    Stream<PaymentRenewal> getCurrentDueRecurringPayments (String clientAlias);

    void scheduleRecurringPayment (AbstractTransactionRevisionRequest request);

    void updateRenewalEntry (String transactionId, String lastSuccessTransactionId, PaymentEvent event, String code, Calendar nextRecurringDateTime,
                                   Transaction transaction, TransactionStatus finalTransactionStatus, PaymentRenewal renewal);

    void createEntryInRenewalTable (String transactionId, String lastSuccessTransactionId, PaymentEvent event, String code, Calendar nextRecurringDateTime, int attemptSequence,
                                   Transaction transaction, TransactionStatus finalTransactionStatus, PaymentRenewal previousRenewal, Transaction previousTransaction);

    void unScheduleRecurringPayment (String clientAlias, String transactionId, PaymentEvent paymentEvent);

    void unScheduleRecurringPayment (Transaction transaction, PaymentEvent paymentEvent, long validUntil, long deferredUntil);

    void upsert (PaymentRenewal paymentRenewal);

    PaymentRenewal getRenewalById (String txnId);

    void scheduleAtbTask (Transaction transaction, Calendar nextRecurringDateTime);

    PaymentRenewal getLatestRecurringPaymentByInitialTxnId (String txnId);

    void updateRenewalSchedule (String clientAlias, String transactionId, Calendar day, Date hour);
}