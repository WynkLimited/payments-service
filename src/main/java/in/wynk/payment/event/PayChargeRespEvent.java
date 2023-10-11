package in.wynk.payment.event;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.event.common.AbstractOrderDetails;
import in.wynk.payment.event.common.EligiblePlanDetails;
import lombok.*;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PayChargeRespEvent<T extends AbstractOrderDetails> {

    private String to;
    private String from;
    private String sessionId;

    private String deeplink;
    private String retailerId;
    private String campaignId;
    private String payConfigId;

    private T orderDetails;

    private EligiblePlanDetails planDetails;

}
