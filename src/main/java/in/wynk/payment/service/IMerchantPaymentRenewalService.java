package in.wynk.payment.service;

import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentRenewalService {

    BaseResponse<?> doRenewal(PaymentRenewalRequest paymentRenewalRequest);

}
