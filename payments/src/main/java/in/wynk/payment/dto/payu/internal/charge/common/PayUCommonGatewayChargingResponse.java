package in.wynk.payment.dto.payu.internal.charge.common;

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
public class PayUCommonGatewayChargingResponse extends PayUGatewayChargingResponse {
    private Map<String, String> form;
}

