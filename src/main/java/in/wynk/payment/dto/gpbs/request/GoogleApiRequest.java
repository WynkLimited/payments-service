package in.wynk.payment.dto.gpbs.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@Setter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GoogleApiRequest {
    @JsonProperty("grant_type")
    private String grantType;
    private String assertion;
}
