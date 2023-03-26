package in.wynk.payment.dto.response.upi;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.response.AbstractPaymentMethodDetails;
import in.wynk.payment.dto.response.SupportingDetails;
import in.wynk.payment.dto.response.WalletSupportingDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.List;
import java.util.Map;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class UPI extends AbstractPaymentMethodDetails {
    @JsonProperty("supporting_details")
    private UpiSupportingDetails supportingDetails;

    @Getter
    @SuperBuilder
    @AnalysedEntity
    public static class UpiSupportingDetails extends SupportingDetails {
        private boolean intent;
        private List<String> suffixes;
        private boolean saveSupported;
        @JsonProperty("payment_status_poll")
        private Integer paymentStatusPoll;
        @JsonProperty("payment_timer")
        private Integer paymentTimer;
        @JsonProperty("package_name")
        private String packageName;
        @JsonProperty("build_check")
        private Map<String, Map<String, Double>> buildCheck;

        @JsonProperty("isSaveSupported")
        public boolean isSaveSupported () {
            return saveSupported;
        }
    }

}

