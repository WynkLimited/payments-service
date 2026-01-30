package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.stream.advice.KafkaEvent;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
@KafkaEvent(topic = "${wynk.data.platform.topic}", transactionName = "unScheduleRecurringPaymentEvent")
public class UnScheduleRecurringPaymentEvent {
    private String transactionId;
    private String clientAlias;
    private String reason;
}
