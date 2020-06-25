package in.wynk.payment.dto.response.paytm;

import in.wynk.payment.dto.response.CustomResponse;
import in.wynk.payment.enums.Status;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class PaytmWalletLinkResponse extends CustomResponse {
    private String state;
}
