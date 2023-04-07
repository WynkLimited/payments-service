package in.wynk.payment.dto.response.upi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.AbstractPaymentMethodDTO;
import in.wynk.payment.dto.response.SupportingDetails;
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
public class UPI extends AbstractPaymentMethodDTO {
    @JsonProperty("supporting_details")
    private UpiSupportingDetails supportingDetails;

    @Getter
    @SuperBuilder
    @AnalysedEntity
    public static class UpiSupportingDetails extends SupportingDetails {
        private boolean intent;
        @JsonProperty("is_custom")
        private boolean custom;
        private List<String> suffixes;
        @JsonProperty("payment_status_poll")
        private Double paymentStatusPoll;
        @JsonProperty("payment_timer")
        private Double paymentTimer;
        @JsonProperty("package_name")
        private String packageName;
        @JsonProperty("build_check")
        private Map<String, Map<String, Integer>> buildCheck;
    }
}

