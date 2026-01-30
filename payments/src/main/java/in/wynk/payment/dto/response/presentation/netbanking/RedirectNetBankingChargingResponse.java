package in.wynk.payment.dto.response.presentation.netbanking;

import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@ToString
@SuperBuilder
public class RedirectNetBankingChargingResponse extends AbstractNetBankingChargingResponse {
    private String redirectUrl;
}
