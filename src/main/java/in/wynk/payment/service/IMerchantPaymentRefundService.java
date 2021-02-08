package in.wynk.payment.service;

import in.wynk.payment.dto.request.AbstractRefundRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentRefundService {

    BaseResponse<?> refund(AbstractRefundRequest request);

}
