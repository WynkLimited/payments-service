package in.wynk.payment.service;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.dto.request.AbstractTransactionRevisionRequest;

import java.util.stream.Stream;

public interface IRecurringPaymentManagerService {

    Stream<PaymentRenewal> getCurrentDueNotifications(String clientAlias);

    Stream<PaymentRenewal> getCurrentDueRecurringPayments(String clientAlias);

    void scheduleRecurringPayment(AbstractTransactionRevisionRequest request);

    void unScheduleRecurringPayment(String clientAlias, String transactionId, PaymentEvent paymentEvent);

    void unScheduleRecurringPayment(String transactionId, PaymentEvent paymentEvent, long validUntil, long deferredUntil);

}