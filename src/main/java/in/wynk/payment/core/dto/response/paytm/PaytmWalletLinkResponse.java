package in.wynk.payment.core.dto.response.paytm;

import in.wynk.payment.core.dto.response.CustomResponse;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class PaytmWalletLinkResponse extends CustomResponse {
    private String state;
}
