package in.wynk.payment.dto.payu.internal.charge;

import in.wynk.payment.dto.common.AbstractPaymentChargingResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public abstract class PayUGatewayChargingResponse extends AbstractPaymentChargingResponse {

}
