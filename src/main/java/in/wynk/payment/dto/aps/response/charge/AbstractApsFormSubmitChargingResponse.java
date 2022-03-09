package in.wynk.payment.dto.aps.response.charge;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class AbstractApsFormSubmitChargingResponse extends AbstractApsExternalChargingResponse {
    @JsonProperty("html")
    private String form;
}
