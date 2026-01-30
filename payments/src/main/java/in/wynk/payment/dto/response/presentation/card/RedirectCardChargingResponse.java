package in.wynk.payment.dto.response.presentation.card;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class RedirectCardChargingResponse extends AbstractCardChargingResponse {
    private String redirectUrl;
}
