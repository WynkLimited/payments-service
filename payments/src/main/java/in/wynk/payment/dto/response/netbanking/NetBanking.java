package in.wynk.payment.dto.response.netbanking;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.AbstractPaymentMethodDTO;
import in.wynk.payment.dto.response.SupportingDetails;
import in.wynk.payment.dto.response.UiDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class NetBanking extends AbstractPaymentMethodDTO {
    @JsonProperty("supporting_details")
    private SupportingDetails supportingDetails;
    @JsonProperty("ui_details")
    private UiDetails uiDetails;
}
