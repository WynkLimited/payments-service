package in.wynk.payment.dto.manager;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.gateway.callback.AbstractPaymentCallbackResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class CallbackResponseWrapper<T extends AbstractPaymentCallbackResponse> extends AbstractPaymentCallbackResponse {
    private T callbackResponse;
    private Transaction transaction;
}
