package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class CallbackRequestWrapper extends CallbackRequest {
    private PaymentCode paymentCode;
}
