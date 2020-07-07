package in.wynk.payment.service.impl;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import org.springframework.stereotype.Service;

@Service(BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE)
public class PayuMerchantPaymentService implements IRenewalMerchantPaymentService {


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
}
