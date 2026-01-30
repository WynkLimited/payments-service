package in.wynk.payment.dto.gateway.charge.upi;

import in.wynk.payment.dto.gateway.IRedirectSpec;
import in.wynk.payment.dto.gateway.charge.AbstractChargingGatewayResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class RedirectUpiChargingGatewayResponse extends AbstractChargingGatewayResponse implements IRedirectSpec<String> {
    private String redirectUrl;
}
