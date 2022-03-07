package in.wynk.payment.dto.response.presentation.upi;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class RedirectUpiChargingResponse extends AbstractUpiChargingResponse {
    private String redirectUrl;
}
