package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.WalletDeLinkRequest;

public interface IWalletDeLinkService<R, T extends WalletDeLinkRequest> {

    WynkResponseEntity<R> deLink(T request);

}
