package in.wynk.payment.dto.gpbs;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@AnalysedEntity
public class GooglePlayReportEvent {
    @Analysed
    private String transactionId;
    @Analysed
    private String paymentEvent;
    @Analysed
    private String service;
    @Analysed
    private Price currentPreTaxAmount;
    @Analysed
    private Price currentTaxAmount;
    @Analysed
    private boolean isTestPurchase;
    @Analysed
    private String createTime;
    @Analysed
    private TransactionState transactionState;
}
