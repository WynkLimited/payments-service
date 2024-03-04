package in.wynk.payment.dto.gpbs;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.gpbs.request.externalTransaction.ExternalTransactionTestPurchase;
import lombok.Builder;
import lombok.Getter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@AnalysedEntity
public class GooglePlayReportEvent {
    private String transactionId;
    private String service;
    private Price currentPreTaxAmount;
    private Price currentTaxAmount;
    private boolean isTestPurchase;
    private String createTime;
    private TransactionState transactionState;
}
