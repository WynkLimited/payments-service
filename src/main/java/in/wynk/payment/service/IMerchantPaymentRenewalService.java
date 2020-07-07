package in.wynk.payment.service;

import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentRenewalService {

    <T> BaseResponse<T> doRenewal(PaymentRenewalRequest paymentRenewalRequest);

}
