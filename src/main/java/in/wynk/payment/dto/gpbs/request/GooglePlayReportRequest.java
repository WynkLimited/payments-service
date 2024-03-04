package in.wynk.payment.dto.gpbs.request;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.gpbs.Price;
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
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public class GooglePlayReportRequest {
    private Price originalPreTaxAmount;
    private Price originalTaxAmount;
    private String transactionTime;
    private ExternalTransactionAddress userTaxAddress;
    private OneTimeExternalTransaction oneTimeTransaction;
    private RecurringExternalTransaction recurringTransaction;
}
