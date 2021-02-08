package in.wynk.payment.service;

import in.wynk.payment.dto.request.AbstractPaymentRefundRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentRefundService {

    BaseResponse<?> refund(AbstractPaymentRefundRequest request);

}
