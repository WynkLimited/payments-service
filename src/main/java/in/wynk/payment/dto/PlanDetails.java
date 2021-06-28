package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public class PlanDetails extends AbstractProductDetails {
    @Analysed
    private int planId;

    @Override
    public String getId() {
        return String.valueOf(planId);
    }

    @Override
    public String getType() {
        return BaseConstants.PLAN;
    }
}
