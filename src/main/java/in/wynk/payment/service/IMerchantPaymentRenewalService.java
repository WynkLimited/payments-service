package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;

public interface IMerchantPaymentRenewalService<R, T extends PaymentRenewalChargingRequest> {

    WynkResponseEntity<R> doRenewal(T paymentRenewalChargingRequest);

    default boolean supportsRenewalReconciliation(){
        return true;
    }

}
