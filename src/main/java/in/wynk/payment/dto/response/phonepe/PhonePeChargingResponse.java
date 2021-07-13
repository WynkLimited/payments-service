package in.wynk.payment.dto.response.phonepe;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class PhonePeChargingResponse extends AbstractChargingResponse {
    private String redirectUrl;
}
