package in.wynk.payment.dto.request.paytm;

import in.wynk.payment.dto.request.WalletRequest;
import lombok.Data;

@Data
public class PaytmWalletAddMoneyRequest extends WalletRequest {
    private String orderId;
    private String uid;
    private Double amountToCredit;
}
