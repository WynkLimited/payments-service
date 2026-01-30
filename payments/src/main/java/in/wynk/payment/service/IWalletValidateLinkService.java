package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.WalletValidateLinkRequest;

public interface IWalletValidateLinkService<R, T extends WalletValidateLinkRequest> {
    WynkResponseEntity<R> validate(T request);
}
