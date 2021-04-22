package in.wynk.payment.dto.response.paytm;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class PaytmWalletLinkResponsePaytm extends PaytmCustomResponse {

    private String state_token;

}
