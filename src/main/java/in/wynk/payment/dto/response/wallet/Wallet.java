package in.wynk.payment.dto.response.wallet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.AbstractPaymentMethodDTO;
import in.wynk.payment.dto.response.UiDetails;
import in.wynk.payment.dto.response.WalletCardSupportingDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class Wallet extends AbstractPaymentMethodDTO {
    @JsonProperty("supporting_details")
    private WalletCardSupportingDetails supportingDetails;
    @JsonProperty("ui_details")
    private UiDetails uiDetails;
}
