package in.wynk.payment.dto.paytm;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.AbstractPaymentRefundRequest;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class PaytmPaymentRefundRequest extends AbstractPaymentRefundRequest {

    private String paytmTxnId;

    public static AbstractPaymentRefundRequest from(Transaction originalTransaction, String externalReferenceId, String reason) {
        return PaytmPaymentRefundRequest.builder()
                .reason(reason)
                .paytmTxnId(externalReferenceId)
                .originalTransactionId(originalTransaction.getIdStr())
                .build();
    }

}