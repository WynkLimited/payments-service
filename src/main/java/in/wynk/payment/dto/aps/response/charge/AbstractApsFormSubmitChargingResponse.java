package in.wynk.payment.dto.aps.response.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class AbstractApsFormSubmitChargingResponse extends AbstractApsExternalChargingResponse {
    @JsonProperty("info")
    private String html;
}
