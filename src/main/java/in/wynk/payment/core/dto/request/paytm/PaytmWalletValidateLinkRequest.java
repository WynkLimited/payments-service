package in.wynk.payment.core.dto.request.paytm;

import in.wynk.payment.core.dto.request.WalletRequest;
import lombok.Getter;

@Getter
public class PaytmWalletValidateLinkRequest extends WalletRequest {

    private String otp;
    private String state;

}
