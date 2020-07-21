package in.wynk.payment.core.dto.request.paytm;

import in.wynk.payment.core.dto.request.WalletRequest;
import lombok.Getter;

@Getter
public class PaytmWalletLinkRequest extends WalletRequest {

    private String encSi;

}
