package in.wynk.payment.service;

import in.wynk.common.enums.PaymentEvent;

public interface ICancellingRecurringService {
    void cancelRecurring(String transactionId, PaymentEvent paymentEvent);
}