package in.wynk.payment.service.impl;

import in.wynk.payment.constant.BeanConstant;
import in.wynk.payment.dto.request.*;
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
    public <T> BaseResponse<T> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
        return null;
    }

    @Override
    public <T> BaseResponse<T> status(ChargingStatusRequest chargingStatusRequest) {
        return null;
    }

    @Override
    public <T> BaseResponse<T> linkRequest(WalletRequest request) {
        return null;
    }

    @Override
    public <T> BaseResponse<T> validateLink(WalletRequest request) {
        return null;
    }

    @Override
    public <T> BaseResponse<T> unlink(WalletRequest request) {
        return null;
    }

    @Override
    public <T> BaseResponse<T> balance() {
        return null;
    }

    @Override
    public <T> BaseResponse<T> addMoney(WalletRequest request) {
        return null;
    }
}
