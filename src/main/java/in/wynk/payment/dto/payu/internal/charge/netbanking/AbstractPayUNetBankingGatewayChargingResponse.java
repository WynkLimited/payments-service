package in.wynk.payment.dto.payu.internal.charge.netbanking;

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
public abstract class AbstractPayUNetBankingGatewayChargingResponse extends PayUGatewayChargingResponse { }

