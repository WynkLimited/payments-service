package in.wynk.payment.core.constant;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import lombok.AllArgsConstructor;

@AllArgsConstructor
@AnalysedEntity
public enum InvoiceState {

    IN_PROGRESS("IN_PROGRESS"),
    SUCCESS("SUCCESS"),
    FAILED("FAILED"),
    UNKNOWN("UNKNOWN");

    @Analysed(name = BaseConstants.INVOICE_STATE)
    private final String invoiceState;
}