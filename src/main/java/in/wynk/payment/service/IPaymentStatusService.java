package in.wynk.payment.service;

import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.request.AbstractTransactionStatusRequest;

public interface IPaymentStatusService<R extends AbstractPaymentStatusResponse, T extends AbstractTransactionStatusRequest> {
    R status (T request);
}