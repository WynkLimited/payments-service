package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@AnalysedEntity
public class PaymentGroupsDTO {
    private String id; //group
    private String title; //display Name
    private String description;
}
