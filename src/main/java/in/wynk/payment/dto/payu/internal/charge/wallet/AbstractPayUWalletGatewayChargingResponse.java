package in.wynk.payment.dto.payu.internal.charge.wallet;

import in.wynk.payment.dto.common.IPostFormSpec;
import in.wynk.payment.dto.payu.internal.charge.PayUGatewayChargingResponse;
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
public class AbstractPayUWalletGatewayChargingResponse extends PayUGatewayChargingResponse implements IPostFormSpec<String, String> {
    private Map<String, String> form;
}
