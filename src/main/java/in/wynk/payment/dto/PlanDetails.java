package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor
public class PlanDetails extends AbstractProductDetails {
    @Analysed
    private int planId;

    @Override
    @JsonIgnore
    public String getId() {
        return String.valueOf(planId);
    }

    @Override
    public String getType() {
        return BaseConstants.PLAN;
    }
}
