package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PlanDetails extends AbstractProductDetails {
    @Analysed
    private int planId;
    @Analysed
    private boolean autoRenew;
    @Analysed
    private boolean trialOpted;
}
