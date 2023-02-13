package in.wynk.payment.dto.response.presentation.card;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class SeamlessCardChargingResponse extends AbstractCardChargingResponse {
    @JsonProperty("info")
    private String form;
}
