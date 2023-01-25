package in.wynk.payment.dto.response.presentation.wallet;

import in.wynk.payment.dto.response.AbstractChargingResponse;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public abstract class AbstractWalletChargingResponse extends AbstractChargingResponse {
}
