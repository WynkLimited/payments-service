package in.wynk.payment.dto.response.phonepe;

import in.wynk.payment.dto.response.AbstractChargingResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class PhonePeChargingResponse extends AbstractChargingResponse {
    private String redirectUrl;
}
