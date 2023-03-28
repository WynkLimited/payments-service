package in.wynk.payment.dto.aps.response.option;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentConfig {
    private String favPayModeCohort;
    private String quickCheckoutAdditionalCohort;
    private String linkedUPIEnabled;
}
