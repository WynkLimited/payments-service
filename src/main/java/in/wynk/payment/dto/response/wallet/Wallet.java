package in.wynk.payment.dto.response.wallet;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.AbstractPaymentMethodDetails;
import in.wynk.payment.dto.response.WalletSupportingDetails;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class Wallet extends AbstractPaymentMethodDetails {
    @JsonProperty("supporting_details")
    private WalletSupportingDetails supportingDetails;
}
