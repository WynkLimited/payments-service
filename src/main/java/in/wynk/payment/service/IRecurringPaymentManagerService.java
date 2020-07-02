package in.wynk.payment.service;

import in.wynk.payment.core.dto.PaymentRenewal;

import java.util.Calendar;
import java.util.stream.Stream;

public interface IRecurringPaymentManagerService {

    PaymentRenewal addRecurringPayment(String transactionId, Calendar nextRecurringDateTime);

    Stream<PaymentRenewal> getCurrentDueRecurringPayments();

}
