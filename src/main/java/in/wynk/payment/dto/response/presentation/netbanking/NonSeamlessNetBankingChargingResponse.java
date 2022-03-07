package in.wynk.payment.dto.response.presentation.netbanking;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class NonSeamlessNetBankingChargingResponse extends AbstractNetBankingChargingResponse {
    @JsonProperty("info")
    private String form;
}
