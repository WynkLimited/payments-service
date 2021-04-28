package in.wynk.payment.dto.paytm;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.Getter;

@Getter
@Deprecated
public class WalletValidateLinkRequest  {
    private PaymentCode paymentCode;
    private String otp;
    private String state;
}