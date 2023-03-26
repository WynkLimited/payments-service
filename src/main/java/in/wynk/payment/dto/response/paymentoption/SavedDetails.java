package in.wynk.payment.dto.response.paymentoption;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
public class SavedDetails {
    private String id;
    private String code;
    private String group;
    @JsonProperty("is_favourite")
    private boolean isFavorite;
    @JsonProperty("is_recommended")
    private boolean isRecommended;
    @JsonProperty("health_status")
    private String health;
    @JsonProperty("auto_pay_enabled")
    private boolean autoPayEnabled;
}
