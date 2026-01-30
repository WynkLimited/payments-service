package in.wynk.payment.dto.gateway.charge.netbanking;

import in.wynk.payment.dto.gateway.IRedirectSpec;
import in.wynk.payment.dto.gateway.charge.card.AbstractCardChargingGatewayResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class RedirectNetBankingChargingGatewayResponse extends AbstractCardChargingGatewayResponse implements IRedirectSpec<String> {
    private String redirectUrl;
}
