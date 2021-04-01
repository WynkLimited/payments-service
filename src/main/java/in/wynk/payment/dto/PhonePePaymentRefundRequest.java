package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.AbstractPaymentRefundRequest;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class PhonePePaymentRefundRequest extends AbstractPaymentRefundRequest {
    private final String ppId;

    public static AbstractPaymentRefundRequest from(Transaction originalTransaction, Transaction refundTransaction, String reason) {
        return PhonePePaymentRefundRequest.builder()
                .reason(reason)
                .originalTransactionId(originalTransaction.getIdStr())
                .ppId(originalTransaction.getValueFromPaymentMetaData(BaseConstants.EXTERNAL_TRANSACTION_ID))
                .build();
    }
}
