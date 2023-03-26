package in.wynk.payment.dto.response.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.AbstractPaymentMethodDetails;
import in.wynk.payment.dto.response.SupportingDetails;
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
public class AddToBill extends AbstractPaymentMethodDetails {
    private SupportingDetails supportingDetails;

    @AllArgsConstructor
    @Builder
    @AnalysedEntity
    public static class BillingSavedDetails {
        private String si;
        private String type;
    }

    @SuperBuilder
    @AnalysedEntity
    public static class BillingSupportingDetails extends SupportingDetails {
        @JsonProperty("otp_required")
        private String otpRequired;
    }
}
