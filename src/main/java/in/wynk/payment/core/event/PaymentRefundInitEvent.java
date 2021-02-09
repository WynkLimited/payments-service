package in.wynk.payment.core.event;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentRefundInitEvent {
    private final String transactionId;
}
