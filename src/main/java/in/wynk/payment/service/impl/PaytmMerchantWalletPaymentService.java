package in.wynk.payment.service.impl;

import in.wynk.payment.constant.BeanConstant;
import in.wynk.payment.dto.request.AddWalletMoneyRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.ConsultBalanceRequest;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.request.SendOtpRequest;
import in.wynk.payment.dto.request.WalletLinkRequest;
import in.wynk.payment.dto.request.WalletUnlinkRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IRenewalMerchantWalletService;
import org.springframework.stereotype.Service;

@Service(BeanConstant.PAYTM_MERCHANT_WALLET_SERVICE)
public class PaytmMerchantWalletPaymentService implements IRenewalMerchantWalletService {


    @Override
    public <T> BaseResponse<T> handleCallback(CallbackRequest callbackRequest) {
        return null;
    }

    @Override
    public <T> BaseResponse<T> doCharging(ChargingRequest chargingRequest) {
        return null;
    }

    @Override
    public <T> BaseResponse<T> status(ChargingStatusRequest chargingStatusRequest) {
        return null;
    }

    @Override
    public <T> BaseResponse<T> link(WalletLinkRequest walletLinkRequest) {
        return null;
    }

    @Override
    public <T> BaseResponse<T> unlink(WalletUnlinkRequest walletUnlinkRequest) {
        return null;
    }

    @Override
    public <T> BaseResponse<T> balance(ConsultBalanceRequest consultBalanceRequest) {
        return null;
    }

    @Override
    public <T> BaseResponse<T> addMoney(AddWalletMoneyRequest addWalletMoneyRequest) {
        return null;
    }

    @Override
    public <T> BaseResponse<T> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
        return null;
    }

    @Override
    public <T> BaseResponse<T> sendOtp(SendOtpRequest sendOtpRequest) {
        return null;
    }
}
