package in.wynk.payment.event.common;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class EligiblePlanDetails extends AbstractPlanDetails {
    private PriceDetails priceDetails;
    private PeriodDetails periodDetails;
}
