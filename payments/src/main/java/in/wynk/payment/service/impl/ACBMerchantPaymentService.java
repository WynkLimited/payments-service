package in.wynk.payment.service.impl;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.error.codes.core.service.IErrorCodesCacheService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.common.request.AbstractPaymentChargingResponse;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.AbstractCallbackResponse;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.service.*;
import org.springframework.stereotype.Service;

@Service(BeanConstant.ACB_MERCHANT_PAYMENT_SERVICE)
public class ACBMerchantPaymentService extends AbstractMerchantPaymentStatusService implements IMerchantPaymentCallbackService<AbstractCallbackResponse, CallbackRequest>, IPaymentChargingService<AbstractPaymentChargingResponse, AbstractChargingRequest<?>>, IMerchantPaymentRenewalService<PaymentRenewalChargingRequest> {

    protected ACBMerchantPaymentService(PaymentCachingService cachingService, IErrorCodesCacheService errorCodesCacheServiceImpl) {
        super(cachingService, errorCodesCacheServiceImpl);
    }

    @Override
    public AbstractPaymentChargingResponse charge(AbstractChargingRequest<?> chargingRequest) {
        throw new WynkRuntimeException(PaymentErrorType.PAY888);
    }

    @Override
    public WynkResponseEntity<Void> doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        Transaction transaction = TransactionContext.get();
        transaction.setStatus(TransactionStatus.SUCCESS.getValue());
        return WynkResponseEntity.<Void>builder().build();
    }

    @Override
    public WynkResponseEntity<AbstractChargingStatusResponse> status(AbstractTransactionReconciliationStatusRequest transactionStatusRequest) {
        throw new WynkRuntimeException(PaymentErrorType.PAY888);
    }

    public boolean supportsRenewalReconciliation() {
        return false;
    }

    @Override
    public WynkResponseEntity<AbstractCallbackResponse> handleCallback(CallbackRequest callbackRequest) {
        throw new WynkRuntimeException(PaymentErrorType.PAY888);
    }

}
