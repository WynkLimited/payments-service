package in.wynk.payment.gateway;

import in.wynk.payment.dto.common.response.AbstractPaymentAccountDeletionResponse;
import in.wynk.payment.dto.request.PaymentAccountDeletionRequest;

/**
 * @author Nishesh Pandey
 */
public interface IPaymentAccountDeletion<R extends AbstractPaymentAccountDeletionResponse, T extends PaymentAccountDeletionRequest> {
    R delete (T PaymentMethodDeleteRequest);
}
