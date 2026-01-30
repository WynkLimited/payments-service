package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.validations.MongoBaseEntityConstraint;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

import static in.wynk.common.constant.CacheBeanNameConstants.PLAN_DTO;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PlanDetails extends AbstractProductDetails {

    @NotNull
    @Analysed
    @MongoBaseEntityConstraint(beanName = PLAN_DTO)
    private Integer planId;

    @Override
    @JsonIgnore
    public String getId() {
        return planId.toString();
    }

    @Override
    public String getType() {
        return BaseConstants.PLAN;
    }

}