package in.wynk.payment.dto.response.presentation.netbanking;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NonSeamlessNetBankingChargingResponse extends AbstractNetBankingChargingResponse {
    @JsonProperty("info")
    private String form;
}
