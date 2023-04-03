package in.wynk.payment.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class WalletCardSupportingDetails extends SupportingDetails {
    @JsonProperty("intent_details")
    private IntentDetails intentDetails;
    private boolean saveSupported;

    @JsonProperty("isSaveSupported")
    public boolean isSaveSupported () {
        return this.saveSupported;
    }

    @Getter
    @AllArgsConstructor
    @SuperBuilder
    @AnalysedEntity
    public static class IntentDetails {
        @JsonProperty("package_name")
        private String packageName;
    }
}
