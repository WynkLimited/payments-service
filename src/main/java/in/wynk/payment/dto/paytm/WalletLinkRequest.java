package in.wynk.payment.dto.paytm;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.WalletRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Deprecated
@NoArgsConstructor
public class WalletLinkRequest {
    private PaymentCode paymentCode;
    private String encSi;

}
