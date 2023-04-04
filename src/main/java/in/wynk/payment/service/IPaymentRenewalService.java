package in.wynk.payment.service;

import in.wynk.payment.dto.PaymentRenewalChargingMessage;

/**
 * @author Nishesh Pandey
 */
public interface IPaymentRenewalService<T extends PaymentRenewalChargingMessage> {
    void renew (T paymentRenewalChargingMessage);
    default boolean supportsRenewalReconciliation () {
        return true;
    }
}
