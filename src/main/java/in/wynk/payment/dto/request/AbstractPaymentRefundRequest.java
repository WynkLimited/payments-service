package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PhonePePaymentRefundRequest;
import in.wynk.payment.dto.payu.PayUPaymentRefundRequest;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public abstract class AbstractPaymentRefundRequest {

    private final String reason;
    private final String originalTransactionId;

    public static AbstractPaymentRefundRequest from(Transaction originalTransaction, Transaction refundTransaction, String reason) {
        switch (originalTransaction.getPaymentChannel()) {
            case PAYU:
                return PayUPaymentRefundRequest.from(originalTransaction, refundTransaction, reason);
            case PHONEPE_WALLET:
                return PhonePePaymentRefundRequest.from(originalTransaction, refundTransaction, reason);
            default:
                throw new WynkRuntimeException(PaymentErrorType.PAY889);
        }
    }

}
