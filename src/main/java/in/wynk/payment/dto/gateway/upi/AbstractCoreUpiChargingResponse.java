package in.wynk.payment.dto.gateway.upi;

import in.wynk.payment.dto.response.AbstractPaymentChargingResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public abstract class AbstractCoreUpiChargingResponse extends AbstractPaymentChargingResponse {
}
