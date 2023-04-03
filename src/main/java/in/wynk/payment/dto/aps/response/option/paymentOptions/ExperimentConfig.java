package in.wynk.payment.dto.aps.response.option.paymentOptions;

import lombok.*;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ExperimentConfig {
    private String favPayModeCohort;
    private String quickCheckoutAdditionalCohort;
    private String linkedUPIEnabled;
}
