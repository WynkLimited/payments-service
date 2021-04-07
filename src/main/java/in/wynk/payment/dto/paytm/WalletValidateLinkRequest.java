package in.wynk.payment.dto.paytm;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.WalletRequest;
import lombok.Getter;

@Getter
public class WalletValidateLinkRequest  {
    private PaymentCode paymentCode;
    private String otp;
    private String state;
}
