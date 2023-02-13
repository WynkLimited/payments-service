package in.wynk.payment.dto.gateway.charge.netbanking;

import in.wynk.payment.dto.gateway.charge.AbstractChargingGatewayResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public abstract class AbstractNetBankingChargingGatewayResponse extends AbstractChargingGatewayResponse {
}
