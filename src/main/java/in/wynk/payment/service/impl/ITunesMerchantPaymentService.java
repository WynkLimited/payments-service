package in.wynk.payment.service.impl;

import in.wynk.payment.constant.BeanConstant;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantPaymentStatusService;
import org.springframework.stereotype.Service;

@Service(BeanConstant.ITUNES_MERCHANT_PAYMENT_SERVICE)
public class ITunesMerchantPaymentService implements IMerchantPaymentStatusService {
    @Override
    public <T> BaseResponse<T> status(ChargingStatusRequest chargingStatusRequest) {
        return null;
    }
}
