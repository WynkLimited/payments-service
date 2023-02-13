package in.wynk.payment.dto.payu.internal.charge.card.redirection;

import in.wynk.payment.dto.common.IPostFormSpec;
import in.wynk.payment.dto.payu.internal.charge.card.AbstractPayUCardGatewayChargingResponse;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.Map;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@SuperBuilder
public class PayUCardGatewayNonSeamlessChargingResponse extends AbstractPayUCardGatewayChargingResponse implements IPostFormSpec<String, String> {
    private Map<String, String> form;
}
