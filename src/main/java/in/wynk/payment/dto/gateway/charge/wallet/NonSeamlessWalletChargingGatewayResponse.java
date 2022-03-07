package in.wynk.payment.dto.gateway.charge.wallet;

import in.wynk.payment.dto.gateway.IPostFormSpec;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Getter
@ToString
@SuperBuilder
public class NonSeamlessWalletChargingGatewayResponse extends AbstractWalletChargingGatewayResponse implements IPostFormSpec<String, String> {
    private Map<String, String> form;
}

