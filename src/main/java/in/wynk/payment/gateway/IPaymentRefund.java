package in.wynk.payment.gateway;

import in.wynk.payment.dto.request.AbstractPaymentRefundRequest;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;

public interface IPaymentRefund<R extends AbstractPaymentRefundResponse, T extends AbstractPaymentRefundRequest> {
    R doRefund(T request);
}
