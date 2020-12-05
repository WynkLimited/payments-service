package in.wynk.payment.service;

import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;

public interface IMerchantPaymentRenewalService {

    void doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest);

    default boolean supportsReconciliation(){
        return true;
    }

}
