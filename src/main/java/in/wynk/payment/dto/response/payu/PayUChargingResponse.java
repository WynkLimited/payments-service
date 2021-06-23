package in.wynk.payment.dto.response.payu;

import in.wynk.payment.dto.response.AbstractChargingResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class PayUChargingResponse extends AbstractChargingResponse {
    private final String info;
}
