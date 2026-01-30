package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.response.AbstractCallbackResponse;

import java.util.Map;

public interface IMerchantPaymentCallbackService<R extends AbstractCallbackResponse,T extends CallbackRequest> {

    WynkResponseEntity<R> handleCallback(T callbackRequest);

    default boolean validate(T callbackRequest) {
        return true;
    }

    default T parseCallback(Map<String, Object> payload) {
        throw new WynkRuntimeException(PaymentErrorType.PAY888);
    }

}
