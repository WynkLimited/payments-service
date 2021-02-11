package in.wynk.payment.event;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.queue.dto.MessageThresholdExceedEvent;
import lombok.Getter;
import lombok.experimental.SuperBuilder;


@Getter
@SuperBuilder
@AnalysedEntity
public abstract class AbstractPaymentMessageThresholdExceedEvent extends MessageThresholdExceedEvent {
}
