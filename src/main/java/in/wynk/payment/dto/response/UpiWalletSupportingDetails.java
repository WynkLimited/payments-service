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
public class UpiWalletSupportingDetails extends SupportingDetails {
    private IntentDetails intentDetails;

    @Getter
    @AllArgsConstructor
    @SuperBuilder
    @AnalysedEntity
    public static class IntentDetails {
        @JsonProperty("package_name")
        private String packageName;
    }
}
