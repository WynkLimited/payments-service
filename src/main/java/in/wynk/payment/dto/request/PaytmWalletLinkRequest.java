package in.wynk.payment.dto.request;

import lombok.Getter;

@Getter
public class PaytmWalletLinkRequest extends WalletRequest {
    private String encSi;
}
