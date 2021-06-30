package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.phonepe.PhonePePaymentRefundRequest;
import in.wynk.payment.dto.paytm.PaytmPaymentRefundRequest;
import in.wynk.payment.dto.payu.PayUPaymentRefundRequest;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public abstract class AbstractPaymentRefundRequest {

    private final String reason;
    private final String originalTransactionId;

    public static AbstractPaymentRefundRequest from(Transaction originalTransaction, String externalReferenceId, String reason) {
        switch (originalTransaction.getPaymentChannel()) {
            case PAYU:
                return PayUPaymentRefundRequest.from(originalTransaction, externalReferenceId, reason);
            case PAYTM_WALLET:
                return PaytmPaymentRefundRequest.from(originalTransaction, externalReferenceId, reason);
            case PHONEPE_WALLET:
                return PhonePePaymentRefundRequest.from(originalTransaction, externalReferenceId, reason);
            default:
                throw new WynkRuntimeException(PaymentErrorType.PAY889);
        }
    }

}