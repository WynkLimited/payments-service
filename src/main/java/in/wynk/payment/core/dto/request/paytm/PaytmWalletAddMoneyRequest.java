package in.wynk.payment.core.dto.request.paytm;

import in.wynk.payment.core.dto.request.WalletRequest;
import lombok.Getter;

@Getter
public class PaytmWalletAddMoneyRequest extends WalletRequest {
    private int planId;
    private double amountToCredit;
}
