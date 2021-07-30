package in.wynk.payment.service.impl;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.service.AbstractMerchantPaymentStatusService;
import in.wynk.payment.service.PaymentCachingService;
import org.springframework.stereotype.Service;

@Service(BeanConstant.GOOGLE_WALLET_MERCHANT_PAYMENT_SERVICE)
public class GoogleWalletMerchantPaymentService extends AbstractMerchantPaymentStatusService {

    protected GoogleWalletMerchantPaymentService(PaymentCachingService cachingService, IErrorCodesCacheService errorCodesCacheServiceImpl) {
        super(cachingService, errorCodesCacheServiceImpl);
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        return null;
    }

}
