package in.wynk.payment.dto.payu;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.AbstractPaymentRefundRequest;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class PayUPaymentRefundRequest extends AbstractPaymentRefundRequest {

    private final String authPayUId;

    public static AbstractPaymentRefundRequest from(Transaction originalTransaction, Transaction refundTransaction, String reason) {
        return PayUPaymentRefundRequest.builder()
                .reason(reason)
                .originalTransactionId(originalTransaction.getIdStr())
                .authPayUId(originalTransaction.getValueFromPaymentMetaData(BaseConstants.EXTERNAL_TRANSACTION_ID))
                .build();
    }
}
