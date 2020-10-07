package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.TransactionEvent;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
@AnalysedEntity
public class RecurringPaymentEvent {

    private final UUID transactionId;
    private final TransactionEvent transactionEvent;

}
