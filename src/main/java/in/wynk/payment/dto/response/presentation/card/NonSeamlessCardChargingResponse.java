package in.wynk.payment.dto.response.presentation.card;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NonSeamlessCardChargingResponse extends AbstractCardChargingResponse {
    @JsonProperty("info")
    private String form;
}
