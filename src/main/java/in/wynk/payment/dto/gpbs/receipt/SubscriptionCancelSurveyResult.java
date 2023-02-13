package in.wynk.payment.dto.gpbs.receipt;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class SubscriptionCancelSurveyResult {
    private String cancelSurveyReason;
    private String userInputCancelReason;
}
