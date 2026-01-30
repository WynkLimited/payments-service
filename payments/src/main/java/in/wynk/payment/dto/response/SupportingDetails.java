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
@AllArgsConstructor
@SuperBuilder
@AnalysedEntity
public class SupportingDetails {
    @JsonProperty("is_save_supported")
    private boolean saveSupported;
    @JsonProperty("auto_renew_supported")
    private boolean autoRenewSupported;
    @JsonProperty("is_mandate_supported")
    private boolean mandateSupported;
}
