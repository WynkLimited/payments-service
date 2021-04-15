package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PaymentRefundInitEvent {
    @Analysed
    private final String reason;
    @Analysed
    private final String originalTransactionId;
}
