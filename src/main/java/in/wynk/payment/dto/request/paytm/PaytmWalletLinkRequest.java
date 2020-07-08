package in.wynk.payment.dto.request.paytm;

import in.wynk.payment.dto.request.WalletRequest;
import lombok.Getter;

@Getter
public class PaytmWalletLinkRequest extends WalletRequest {

    private String encSi;

}
