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
    private String paymentEvent;
    @Analysed
    private String clientAlias;
    @Analysed
    private String errorReason;
    @Analysed
    private String referenceTransactionId;

    public PaymentEvent getEvent() {
        return PaymentEvent.valueOf(paymentEvent);
    }


}
