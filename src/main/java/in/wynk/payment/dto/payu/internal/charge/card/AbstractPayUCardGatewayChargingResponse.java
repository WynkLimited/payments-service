package in.wynk.payment.dto.payu.internal.charge.card;

import in.wynk.payment.dto.payu.internal.charge.PayUGatewayChargingResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public abstract class AbstractPayUCardGatewayChargingResponse extends PayUGatewayChargingResponse {
}
