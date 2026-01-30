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
public abstract class AbstractPaymentMethodDTO {
    private String id;
    private String code;
    private String title;
    private String health;
    private String group;
    private Integer order;
    private String description;
    @JsonProperty("ui_details")
    private UiDetails uiDetails;
    @JsonProperty("supporting_details")
    private SupportingDetails supportingDetails;
}
