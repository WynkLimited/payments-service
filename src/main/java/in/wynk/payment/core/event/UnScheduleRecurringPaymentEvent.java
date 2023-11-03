package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
public class UnScheduleRecurringPaymentEvent {
    private String transactionId;
    private String clientAlias;
    private String reason;
}
