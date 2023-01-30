package in.wynk.payment.dto.response.upi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.PaymentMethodDetails;
import in.wynk.payment.dto.response.UpiWalletSupportingDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class UPI extends PaymentMethodDetails {
    @JsonProperty("saved_details")
    private List<UpiSavedDetails> savedDetails;
    @JsonProperty("alert_details")
    private final Object alertDetails; // "low success rate","kyc required"
    @JsonProperty("supporting_details")
    private UpiWalletSupportingDetails supportingDetails;
}

