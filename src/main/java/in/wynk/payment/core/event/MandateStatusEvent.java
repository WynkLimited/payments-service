package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
public class MandateStatusEvent {
    @Analysed
    private String txnId;
    @Analysed
    private String clientAlias;
    @Analysed
    private String errorReason;
    @Analysed
    private int planId;
    @Analysed
    private String uid;
    @Analysed
    private String referenceTransactionId;


}
