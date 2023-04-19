package in.wynk.payment.dto.payu.internal.charge.card;

import in.wynk.payment.dto.payu.internal.charge.PayUGatewayChargingResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
public abstract class AbstractPayUCardGatewayChargingResponse extends PayUGatewayChargingResponse {
}
