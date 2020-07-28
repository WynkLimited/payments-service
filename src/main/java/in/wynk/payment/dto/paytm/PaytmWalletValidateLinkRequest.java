package in.wynk.payment.dto.paytm;

import in.wynk.payment.dto.request.WalletRequest;
import lombok.Getter;

@Getter
public class PaytmWalletValidateLinkRequest extends WalletRequest {

    private String otp;
    private String state;

}
