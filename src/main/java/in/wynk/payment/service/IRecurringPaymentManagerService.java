package in.wynk.payment.service;

import in.wynk.payment.core.dao.entity.PaymentRenewal;

import java.util.Calendar;
import java.util.UUID;
import java.util.stream.Stream;

public interface IRecurringPaymentManagerService {

    PaymentRenewal scheduleRecurringPayment(UUID transactionId, Calendar nextRecurringDateTime);

    Stream<PaymentRenewal> getCurrentDueRecurringPayments();

    void unScheduleRecurringPayment(UUID transactionId);

}
