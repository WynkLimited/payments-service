package in.wynk.payment.service;

import in.wynk.payment.dto.common.response.AbstractPaymentMethodDeleteResponse;
import in.wynk.payment.dto.request.PaymentMethodDeleteRequest;

/**
 * @author Nishesh Pandey
 */
public interface IPaymentDeleteService<R extends AbstractPaymentMethodDeleteResponse, T extends PaymentMethodDeleteRequest> {
    R delete (T PaymentMethodDeleteRequest);
}
