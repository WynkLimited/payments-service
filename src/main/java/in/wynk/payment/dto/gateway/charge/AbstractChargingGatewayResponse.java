package in.wynk.payment.dto.gateway.charge;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public abstract class AbstractChargingGatewayResponse {
    private String tid;
    private TransactionStatus transactionStatus;
    private PaymentEvent transactionType;
}
