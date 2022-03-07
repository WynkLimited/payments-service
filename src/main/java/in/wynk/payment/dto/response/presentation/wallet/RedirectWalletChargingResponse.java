package in.wynk.payment.dto.response.presentation.wallet;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class RedirectWalletChargingResponse extends AbstractWalletChargingResponse {
    private String redirectUrl;
}
