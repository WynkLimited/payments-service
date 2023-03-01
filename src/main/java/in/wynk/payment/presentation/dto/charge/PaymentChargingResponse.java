package in.wynk.payment.presentation.dto.charge;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.TransactionStatus;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public abstract class PaymentChargingResponse {
    private String tid;
    private TransactionStatus transactionStatus;
    private final String transactionType;
    private String action;
}