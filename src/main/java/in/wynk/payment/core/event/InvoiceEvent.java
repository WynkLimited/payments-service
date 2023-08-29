package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;
import java.util.Calendar;

@Getter
@Builder
@AnalysedEntity
public class InvoiceEvent {
    @Analysed
    private String clientAlias;
    @Analysed
    private String invoiceId;
    @Analysed
    private String transactionId;
    @Analysed
    private String invoiceExternalId;
    @Analysed
    private double amount;
    @Analysed
    private double taxAmount;
    @Analysed
    private double taxableValue;
    @Analysed
    private double cgst;
    @Analysed
    private double sgst;
    @Analysed
    private double igst;
    @Analysed
    private String state;
    @Analysed
    private Calendar createdOn;
    @Analysed
    private Calendar updatedOn;
    @Analysed
    private int retryCount;
}