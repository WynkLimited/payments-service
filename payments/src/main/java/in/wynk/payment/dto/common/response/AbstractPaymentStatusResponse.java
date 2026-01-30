package in.wynk.payment.dto.common.response;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@Getter
@Setter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public abstract class AbstractPaymentStatusResponse {
    private String tid;
    private TransactionStatus transactionStatus;
    private PaymentEvent transactionType;
}