package in.wynk.payment.service.impl;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dto.request.CallbackRequest;
import in.wynk.payment.core.dto.request.ChargingRequest;
import in.wynk.payment.core.dto.request.ChargingStatusRequest;
import in.wynk.payment.core.dto.request.PaymentRenewalRequest;
import in.wynk.payment.core.dto.response.BaseResponse;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import org.springframework.stereotype.Service;

@Service(BeanConstant.ACB_MERCHANT_PAYMENT_SERVICE)
public class ACBMerchantPaymentService implements IRenewalMerchantPaymentService {

    @Override
    public BaseResponse<?> handleCallback(CallbackRequest callbackRequest) {
        return null;
    }

    @Override
    public BaseResponse<?> doCharging(ChargingRequest chargingRequest) {
        return null;
    }

    @Override
    public BaseResponse<?> doRenewal(PaymentRenewalRequest paymentRenewalRequest) {
        return null;
    }

    @Override
    public BaseResponse<?> status(ChargingStatusRequest chargingStatusRequest) {
        return null;
    }
}
