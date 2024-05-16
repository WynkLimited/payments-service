package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Builder;
import lombok.Getter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@AnalysedEntity
public class ExternalTransactionReportEvent {
    @Analysed
    private String transactionId;
    @Analysed
    private String clientAlias;
    @Analysed
    private String externalTokenReferenceId;
    @Analysed
    private PaymentEvent paymentEvent;
    @Analysed
    private String initialTransactionId;
}
