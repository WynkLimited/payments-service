package in.wynk.payment.dto.gpbs.response.receipt;

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
