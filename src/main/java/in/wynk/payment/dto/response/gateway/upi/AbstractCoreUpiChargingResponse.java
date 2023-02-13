package in.wynk.payment.dto.response.gateway.upi;

import in.wynk.payment.dto.response.AbstractCoreChargingResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public abstract class AbstractCoreUpiChargingResponse extends AbstractCoreChargingResponse {
}
