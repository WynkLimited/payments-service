package in.wynk.payment.gateway;

import in.wynk.payment.dto.request.AbstractCancelMandateRequest;

/**
 * @author Nishesh Pandey
 */
public interface IPaymentMandateCancellation<T extends AbstractCancelMandateRequest> {
    void cancel(T request);
}
