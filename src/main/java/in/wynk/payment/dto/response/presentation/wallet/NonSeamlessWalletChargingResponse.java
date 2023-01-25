package in.wynk.payment.dto.response.presentation.wallet;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NonSeamlessWalletChargingResponse extends AbstractWalletChargingResponse {
    @JsonProperty("info")
    private String form;
}
