package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@AnalysedEntity
public class MandateStatusEvent {
    @Analysed
    private String txnId;
    @Analysed
    private String referenceTransactionId;
    @Analysed
    private String uid;
    @Analysed
    private Integer planId;
    @Analysed
    private String clientAlias;
    @Analysed
    private String errorReason;
}
