package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class PaymentMethodDetails extends PaymentGroupsDTO {
    private String code;
    @JsonProperty("ui_details")
    private UiDetails uiDetails;
}
