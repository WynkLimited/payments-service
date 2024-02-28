package in.wynk.payment.dto.gpbs.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.gpbs.request.externalTransaction.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GooglePlayReportRequest {
    private String packageName;
    private String externalTransactionId;
    private Price originalPreTaxAmount;
    private Price originalTaxAmount;
    private Price currentPreTaxAmount;
    private Price currentTaxAmount;
    private String transactionTime;
    private String createTime;
    private TransactionState transactionState;
    private ExternalTransactionAddress userTaxAddress;

    private ExternalTransactionTestPurchase testPurchase;
    private OneTimeExternalTransaction oneTimeTransaction;
    private RecurringExternalTransaction recurringTransaction;
}
