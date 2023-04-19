package in.wynk.payment.gateway;

import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import in.wynk.payment.dto.request.CallbackRequest;

import java.util.Map;

public interface IPaymentCallback<R extends AbstractPaymentCallbackResponse, T extends CallbackRequest> {
    R handle(T callbackRequest);
    default T parse(Map<String, Object> payload) {
        throw new WynkRuntimeException(PaymentErrorType.PAY888);
    }
    default <T> boolean isValid(T request) {
        throw new WynkRuntimeException(PaymentErrorType.PAY888);
    }
}
