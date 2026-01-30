package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.AbstractPaymentRefundRequest;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class ApsPaymentRefundRequest extends AbstractPaymentRefundRequest {

    private String pgId;

    public static AbstractPaymentRefundRequest from(Transaction originalTransaction, String externalReferenceId, String reason) {
        return ApsPaymentRefundRequest.builder()
                .reason(reason)
                .pgId(externalReferenceId)
                .originalTransactionId(originalTransaction.getIdStr())
                .build();
    }

}
