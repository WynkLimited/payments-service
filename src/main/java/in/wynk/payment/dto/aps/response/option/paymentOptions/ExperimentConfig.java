package in.wynk.payment.dto.aps.response.option.paymentOptions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.io.Serializable;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExperimentConfig implements Serializable {
    private String favPayModeCohort;
    private String quickCheckoutAdditionalCohort;
    private String linkedUPIEnabled;
}
