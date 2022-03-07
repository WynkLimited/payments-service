package in.wynk.payment.dto.response.presentation.card;

import in.wynk.payment.dto.response.AbstractChargingResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public abstract class AbstractCardChargingResponse extends AbstractChargingResponse {
}
