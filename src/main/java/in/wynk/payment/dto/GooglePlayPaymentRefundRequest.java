package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.AbstractPaymentRefundRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public class GooglePlayPaymentRefundRequest extends AbstractPaymentRefundRequest {

    public static AbstractPaymentRefundRequest from (Transaction originalTransaction, String externalReferenceId, String reason) {
        return GooglePlayPaymentRefundRequest.builder()
                .reason(reason)
                .originalTransactionId(originalTransaction.getIdStr())
                .build();
    }
}
