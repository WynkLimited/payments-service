package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.AbstractPaymentRefundRequest;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantPaymentRefundService<R extends AbstractPaymentRefundResponse, T extends AbstractPaymentRefundRequest> {

    WynkResponseEntity<R> refund(T request);

}
