package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.dto.request.WalletBalanceRequest;
import in.wynk.payment.dto.response.UserWalletDetails;

public interface IWalletBalanceService<R extends UserWalletDetails, T extends WalletBalanceRequest> {
    WynkResponseEntity<R> balance(T request);
}
