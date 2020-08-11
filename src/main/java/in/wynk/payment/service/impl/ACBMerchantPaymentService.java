package in.wynk.payment.service.impl;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.ChargingRequest;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import org.springframework.stereotype.Service;

@Service(BeanConstant.ACB_MERCHANT_PAYMENT_SERVICE)
public class ACBMerchantPaymentService implements IRenewalMerchantPaymentService {

    @Override
    public BaseResponse<?> handleCallback(CallbackRequest callbackRequest, Transaction transaction) {
        return null;
    }

    @Override
    public BaseResponse<?> doCharging(ChargingRequest chargingRequest, Transaction transaction) {
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
