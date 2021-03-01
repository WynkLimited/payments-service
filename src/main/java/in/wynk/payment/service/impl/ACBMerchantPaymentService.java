package in.wynk.payment.service.impl;

import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IRenewalMerchantPaymentService;
import org.springframework.stereotype.Service;

@Service(BeanConstant.ACB_MERCHANT_PAYMENT_SERVICE)
public class ACBMerchantPaymentService implements IRenewalMerchantPaymentService {

    @Override
    public BaseResponse<?> handleCallback(CallbackRequest callbackRequest) {
        throw new WynkRuntimeException(PaymentErrorType.PAY888);
    }

    @Override
    public BaseResponse<?> doCharging(ChargingRequest chargingRequest) {
        throw new WynkRuntimeException(PaymentErrorType.PAY888);
    }

    @Override
    public void doRenewal(PaymentRenewalChargingRequest paymentRenewalChargingRequest) {
        Transaction transaction = TransactionContext.get();
        transaction.setStatus(TransactionStatus.SUCCESS.getValue());
    }

    @Override
    public BaseResponse<?> status(AbstractTransactionStatusRequest chargingStatusRequest) {
        throw new WynkRuntimeException(PaymentErrorType.PAY888);
    }

    public boolean supportsRenewalReconciliation(){
        return false;
    }

}
