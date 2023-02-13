package in.wynk.payment.dto.response.netbanking;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.PaymentMethodDetails;
import in.wynk.payment.dto.response.SupportingDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class NetBanking extends PaymentMethodDetails {
    @JsonProperty("alert_details")
    private List<Object> alertDetails;
    @JsonProperty("supporting_details")
    private SupportingDetails supportingDetails;
}
