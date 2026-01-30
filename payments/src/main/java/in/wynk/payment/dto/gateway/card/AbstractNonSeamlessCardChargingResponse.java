package in.wynk.payment.dto.gateway.card;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public abstract class AbstractNonSeamlessCardChargingResponse extends AbstractCoreCardChargingResponse{
}
