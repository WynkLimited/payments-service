package in.wynk.payment.dto.response.presentation.wallet;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SeamlessWalletChargingResponse extends AbstractWalletChargingResponse {

}
