package in.wynk.payment.dto.phonepe;

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
public class PhonePePaymentRefundRequest extends AbstractPaymentRefundRequest {

    private String ppId;

    public static AbstractPaymentRefundRequest from(Transaction originalTransaction, String externalReferenceId, String reason) {
        return PhonePePaymentRefundRequest.builder()
                .reason(reason)
                .ppId(externalReferenceId)
                .originalTransactionId(originalTransaction.getIdStr())
                .build();
    }

}