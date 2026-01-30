package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;

public interface IMerchantPaymentRenewalService<T extends PaymentRenewalChargingRequest> {

    WynkResponseEntity<Void> doRenewal(T paymentRenewalChargingRequest);

    default boolean supportsRenewalReconciliation(){
        return true;
    }

}
