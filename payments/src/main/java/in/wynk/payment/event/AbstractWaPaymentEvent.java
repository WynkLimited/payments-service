package in.wynk.payment.event;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.event.common.AbstractWaOrderDetails;
import in.wynk.payment.event.common.EligiblePlanDetails;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class AbstractWaPaymentEvent<T extends AbstractWaOrderDetails> {
    private String to;
    private String from;
    private String sessionId;
    private String campaignId;

    private T orderDetails;

    private EligiblePlanDetails planDetails;
}
