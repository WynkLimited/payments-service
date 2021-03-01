package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.payu.PayUPaymentRefundRequest;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public abstract class AbstractPaymentRefundRequest {
    
    private final String originalTransactionId;

    public static AbstractPaymentRefundRequest from(Transaction originalTransaction, Transaction refundTransaction) {
        switch (originalTransaction.getPaymentChannel()) {
            case PAYU:
                return PayUPaymentRefundRequest.from(originalTransaction, refundTransaction);
            default:
                throw new WynkRuntimeException(PaymentErrorType.PAY889);
        }
    }

}
