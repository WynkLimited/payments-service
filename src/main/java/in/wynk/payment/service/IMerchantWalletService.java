package in.wynk.payment.service;

import in.wynk.payment.dto.paytm.WalletAddMoneyRequest;
import in.wynk.payment.dto.paytm.WalletLinkRequest;
import in.wynk.payment.dto.paytm.WalletValidateLinkRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantWalletService {

    BaseResponse<?> linkRequest(WalletLinkRequest request);

    BaseResponse<?> validateLink(WalletValidateLinkRequest request);

    BaseResponse<?> unlink();

    BaseResponse<?> balance();

    BaseResponse<?> addMoney(WalletAddMoneyRequest request);

}
