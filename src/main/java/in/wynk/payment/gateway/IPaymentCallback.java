package in.wynk.payment.gateway;

import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.request.CallbackRequest;

public interface IPaymentCallback<R extends AbstractPaymentCallbackResponse, T extends CallbackRequest> {
    R handleCallback(T callbackRequest);
}
