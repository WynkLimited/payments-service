package in.wynk.payment.service;

import in.wynk.payment.dto.request.AddWalletMoneyRequest;
import in.wynk.payment.dto.request.ConsultBalanceRequest;
import in.wynk.payment.dto.request.WalletLinkRequest;
import in.wynk.payment.dto.request.WalletUnlinkRequest;
import in.wynk.payment.dto.response.BaseResponse;

public interface IMerchantWalletService extends IMerchantOtpService {

    <T> BaseResponse<T> link(WalletLinkRequest walletLinkRequest);

    <T> BaseResponse<T> unlink(WalletUnlinkRequest walletUnlinkRequest);

    <T> BaseResponse<T> balance(ConsultBalanceRequest consultBalanceRequest);

    <T> BaseResponse<T> addMoney(AddWalletMoneyRequest addWalletMoneyRequest);

}
