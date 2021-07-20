package in.wynk.payment.dto.payu;

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
public class PayUPaymentRefundRequest extends AbstractPaymentRefundRequest {

    private String authPayUId;

    public static AbstractPaymentRefundRequest from(Transaction originalTransaction, String externalReferenceId, String reason) {
        return PayUPaymentRefundRequest.builder()
                .reason(reason)
                .authPayUId(externalReferenceId)
                .originalTransactionId(originalTransaction.getIdStr())
                .build();
    }

}