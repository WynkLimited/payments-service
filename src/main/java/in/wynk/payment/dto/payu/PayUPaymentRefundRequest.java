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

    public static AbstractPaymentRefundRequest from(Transaction mainTransaction, Transaction refundTransaction) {
        return PayUPaymentRefundRequest.builder()
                .uid(mainTransaction.getUid())
                .planId(mainTransaction.getPlanId())
                .itemId(mainTransaction.getItemId())
                .amount(refundTransaction.getAmount())
                .refundTransactionId(refundTransaction.getIdStr())
                .authPayUId(mainTransaction.getValueFromPaymentMetaData(BaseConstants.EXTERNAL_TRANSACTION_ID))
                .build();
    }
}
