package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.WalletTopUpRequest;

public interface IWalletTopUpService<R, T extends WalletTopUpRequest<?>> {
    WynkResponseEntity<R> topUp(T request);
}
