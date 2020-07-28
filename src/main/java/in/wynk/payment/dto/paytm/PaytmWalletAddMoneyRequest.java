package in.wynk.payment.dto.paytm;

import in.wynk.payment.dto.request.WalletRequest;
import lombok.Getter;

@Getter
public class PaytmWalletAddMoneyRequest extends WalletRequest {
    private int planId;
    private double amountToCredit;
}
