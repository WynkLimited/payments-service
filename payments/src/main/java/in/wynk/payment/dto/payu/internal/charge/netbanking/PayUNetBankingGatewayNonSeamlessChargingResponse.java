package in.wynk.payment.dto.payu.internal.charge.netbanking;

import in.wynk.payment.dto.common.IPostFormSpec;
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
public class PayUNetBankingGatewayNonSeamlessChargingResponse extends AbstractPayUNetBankingGatewayChargingResponse implements IPostFormSpec<String, String> {
    private Map<String, String> form;
}

