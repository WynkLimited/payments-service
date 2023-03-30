package in.wynk.payment.dto.response.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.AbstractPaymentMethodDetails;
import in.wynk.payment.dto.response.WalletCardSupportingDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class Card extends AbstractPaymentMethodDetails {
    @JsonProperty("ui_details")
    private Map<String, String> uiDetails;
    private WalletCardSupportingDetails supportingDetails;
}
