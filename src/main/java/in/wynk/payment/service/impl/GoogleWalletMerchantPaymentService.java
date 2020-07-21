package in.wynk.payment.service.impl;

import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dto.request.ChargingStatusRequest;
import in.wynk.payment.core.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantPaymentStatusService;
import org.springframework.stereotype.Service;

@Service(BeanConstant.GOOGLE_WALLET_MERCHANT_PAYMENT_SERVICE)
public class GoogleWalletMerchantPaymentService implements IMerchantPaymentStatusService {

    @Override
    public BaseResponse<?> status(ChargingStatusRequest chargingStatusRequest) {
        return null;
    }

}
