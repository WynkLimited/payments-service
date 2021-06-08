package in.wynk.payment.service;

import in.wynk.payment.dto.request.WalletAddMoneyRequest;
import in.wynk.payment.dto.request.WalletLinkRequest;
import in.wynk.payment.dto.request.WalletValidateLinkRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantWalletService {

    BaseResponse<?> linkRequest(WalletLinkRequest request);

    BaseResponse<?> validateLink(WalletValidateLinkRequest request);

    BaseResponse<?> unlink();

    BaseResponse<?> balance(int planId);

    BaseResponse<?> addMoney(WalletAddMoneyRequest request);

}