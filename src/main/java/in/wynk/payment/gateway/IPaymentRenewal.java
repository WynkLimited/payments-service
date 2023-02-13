package in.wynk.payment.gateway;

import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;

public interface IPaymentRenewal<T extends PaymentRenewalChargingRequest> {
    void doRenewal(T paymentRenewalChargingRequest);
    default boolean supportsRenewalReconciliation(){
        return true;
    }
}
