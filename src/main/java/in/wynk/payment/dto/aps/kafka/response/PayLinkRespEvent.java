package in.wynk.payment.dto.aps.kafka.response;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public class PayLinkRespEvent<T extends AbstractOrderDetails> extends AbstractPayInboundEvent {

    private String deeplink;
    private String retailerId;
    private String campaignId;
    private String payConfigId;

    private T orderDetails;

    private EligiblePlanDetails planDetails;

    @Override
    public IMessageType getType() {
        return PayInboundType.PAY_RESP_LINK;
    }
}
