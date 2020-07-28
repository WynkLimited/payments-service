package in.wynk.payment.service;

import in.wynk.payment.dto.request.WalletRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantWalletService {

    <T> BaseResponse<T> linkRequest(WalletRequest request);

    <T> BaseResponse<T> validateLink(WalletRequest request);

    <T> BaseResponse<T> unlink(WalletRequest request);

    <T> BaseResponse<T> balance();

    <T> BaseResponse<T> addMoney(WalletRequest request);

}
