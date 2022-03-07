package in.wynk.payment.dto.gateway.charge.card;

import in.wynk.payment.dto.gateway.IPostFormSpec;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

@Getter
@ToString
@SuperBuilder
public class NonSeamlessCardChargingGatewayResponse extends AbstractCardChargingGatewayResponse implements IPostFormSpec<String, String> {
    private Map<String, String> form;
}
