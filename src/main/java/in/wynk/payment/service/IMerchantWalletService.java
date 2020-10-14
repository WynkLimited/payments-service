package in.wynk.payment.service;

import in.wynk.payment.dto.request.WalletRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantWalletService {

    BaseResponse<?> linkRequest(WalletRequest request);

    BaseResponse<?> validateLink(WalletRequest request);

    BaseResponse<?> unlink(WalletRequest request);

    BaseResponse<?> balance();

    BaseResponse<?> addMoney(WalletRequest request);

}
