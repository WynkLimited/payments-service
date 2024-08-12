package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.stream.dto.MessageThresholdExceedEvent;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class PaymentRenewalMessageThresholdExceedEvent extends MessageThresholdExceedEvent {

    @Analysed
    private int attemptSequence;
    @Analysed
    private String transactionId;
    @Analysed
    private String clientAlias;

}