package in.wynk.payment.dto.paytm;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.WalletRequest;
import lombok.Getter;

@Getter
public class WalletAddMoneyRequest {
    private PaymentCode paymentCode;
    private int planId;
    private double amountToCredit;
    private long txnAmount;
    private long phonePeVersionCode;
}
