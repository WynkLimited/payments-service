package in.wynk.payment.dto.gateway.charge.card;

import in.wynk.payment.dto.gateway.IRedirectSpec;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class RedirectCardChargingGatewayResponse extends AbstractCardChargingGatewayResponse implements IRedirectSpec<String> {
    private String redirectUrl;
}
