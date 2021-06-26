package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PlanDetails implements IProductDetails {
    @Analysed
    private int planId;
    @Analysed
    private boolean autoRenew;
    @Analysed
    private boolean trialOpted;

    @Override
    public String getType() {
        return BaseConstants.PLAN;
    }
}
