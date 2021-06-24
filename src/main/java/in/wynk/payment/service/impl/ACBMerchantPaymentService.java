package in.wynk.payment.service.impl;

import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.request.AbstractChargingRequest;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.CallbackRequest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.dto.response.AbstractCallbackResponse;
import in.wynk.payment.dto.response.AbstractChargingResponse;
import in.wynk.payment.dto.response.AbstractChargingStatusResponse;
import in.wynk.payment.service.*;
import org.springframework.stereotype.Service;

@Service(BeanConstant.ACB_MERCHANT_PAYMENT_SERVICE)
public class ACBMerchantPaymentService extends AbstractMerchantPaymentStatusService implements IMerchantPaymentCallbackService<AbstractCallbackResponse, CallbackRequest>, IMerchantPaymentChargingService<AbstractChargingResponse, AbstractChargingRequest<?>>, IMerchantPaymentRenewalService<Void, PaymentRenewalChargingRequest> {

    protected ACBMerchantPaymentService(PaymentCachingService cachingService) {
        super(cachingService);
    }

    @Override
    public WynkResponseEntity<AbstractChargingResponse> doCharging(AbstractChargingRequest<?> chargingRequest) {
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

    public boolean supportsRenewalReconciliation(){
        return false;
    }

    @Override
    public WynkResponseEntity<AbstractCallbackResponse> handleCallback(CallbackRequest callbackRequest) {
        throw new WynkRuntimeException(PaymentErrorType.PAY888);
    }
}
