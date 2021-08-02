package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.WalletLinkRequest;

public interface IWalletLinkService<R, T extends WalletLinkRequest> {

    WynkResponseEntity<R> link(WalletLinkRequest request);

}
