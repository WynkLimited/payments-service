package in.wynk.payment.dto.gpbs.response.externalTransaction;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.gpbs.request.externalTransaction.ExternalTransactionTestPurchase;
import in.wynk.payment.dto.gpbs.Price;
import in.wynk.payment.dto.gpbs.TransactionState;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GooglePlayReportResponse {
    private String packageName;
    private String externalTransactionId;
    private Price currentPreTaxAmount;
    private Price currentTaxAmount;
    private ExternalTransactionTestPurchase testPurchase;
    private String createTime;
    private TransactionState transactionState;
}
