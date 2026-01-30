package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
public class PaymentUserDeactivationMigrationEvent {
    @Analysed
    private final String id;
    @Analysed
    private final String uid;
    @Analysed
    private final String oldUid;
}
