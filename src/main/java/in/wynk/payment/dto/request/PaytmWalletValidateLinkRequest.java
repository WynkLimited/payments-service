package in.wynk.payment.dto.request;

import lombok.Getter;

@Getter
public class PaytmWalletValidateLinkRequest extends WalletRequest {

    private String otp;

}
