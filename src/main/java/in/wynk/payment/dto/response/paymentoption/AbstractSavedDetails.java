package in.wynk.payment.dto.response.paymentoption;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@NoArgsConstructor
@SuperBuilder
public abstract class AbstractSavedDetails {
    private String id;
    private String code;
    @JsonProperty("is_favourite")
    private boolean isFavorite;
    @JsonProperty("is_recommended")
    private boolean isRecommended;
    @JsonProperty("health_status")
    private String health;
    @JsonProperty("auto_pay_enabled")
    private boolean autoPayEnabled;
}
