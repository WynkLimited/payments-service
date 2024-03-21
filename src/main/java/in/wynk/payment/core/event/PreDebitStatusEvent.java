package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.PaymentEvent;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
public class PreDebitStatusEvent {
    @Analysed
    private String txnId;
    @Analysed
    private String type;
    @Analysed
    private String clientAlias;
    @Analysed
    private String errorReason;

    public PaymentEvent getType() {
        return PaymentEvent.valueOf(type);
    }

}
