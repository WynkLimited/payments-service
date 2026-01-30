package in.wynk.payment.dto.gpbs.acknowledge.request;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class GooglePlaySubscriptionAcknowledgementRequest extends AbstractAcknowledgement {
    @Override
    public String getType () {
        return BaseConstants.PLAN;
    }
}
