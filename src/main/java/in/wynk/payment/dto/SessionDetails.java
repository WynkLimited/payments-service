package in.wynk.payment.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.github.annotation.analytic.core.annotations.Analysed;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class SessionDetails {

    @JsonProperty("sid")
    @Analysed
    private String sessionId;
}
