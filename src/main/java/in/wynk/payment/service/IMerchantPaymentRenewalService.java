package in.wynk.payment.service;

import in.wynk.payment.core.dto.request.PaymentRenewalRequest;
import in.wynk.payment.core.dto.response.BaseResponse;

public interface IMerchantPaymentRenewalService {

    BaseResponse<?> doRenewal(PaymentRenewalRequest paymentRenewalRequest);

}
