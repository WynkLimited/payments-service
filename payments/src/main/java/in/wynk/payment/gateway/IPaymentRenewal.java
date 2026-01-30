package in.wynk.payment.gateway;

import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;

public interface IPaymentRenewal<T extends PaymentRenewalChargingRequest> {
    void renew(T paymentRenewalChargingRequest);
    default boolean canRenewalReconciliation(){
        return true;
    }
}
