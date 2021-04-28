package in.wynk.payment.dto.paytm;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.WalletRequest;
import lombok.Getter;

@Getter
@Deprecated
public class WalletAddMoneyRequest {
    private PaymentCode paymentCode;
    private int planId;
    private String itemId;
    private double amountToCredit;;
    private long phonePeVersionCode;
}
