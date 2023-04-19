package in.wynk.payment.gateway;

import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;

public interface IPaymentStatus<R extends AbstractPaymentStatusResponse, T extends AbstractTransactionStatusRequest> {
    R reconcile (T request);
}