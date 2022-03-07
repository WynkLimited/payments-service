package in.wynk.payment.dto.response.presentation.wallet;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class NonSeamlessWalletChargingResponse extends AbstractWalletChargingResponse {
    @JsonProperty("info")
    private String form;
}
