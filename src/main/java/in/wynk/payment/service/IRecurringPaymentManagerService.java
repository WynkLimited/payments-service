package in.wynk.payment.service;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.PaymentRenewal;

import java.util.Calendar;
import java.util.stream.Stream;

public interface IRecurringPaymentManagerService {

    PaymentRenewal scheduleRecurringPayment(String transactionId, Calendar nextRecurringDateTime);

    Stream<PaymentRenewal> getCurrentDueRecurringPayments();

    void unScheduleRecurringPayment(String transactionId, PaymentEvent paymentEvent, long validTillDate);

}
