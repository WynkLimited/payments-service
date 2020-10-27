package in.wynk.payment.service;

import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentRenewalService {

    BaseResponse<?> doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest);

}
