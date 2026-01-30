package in.wynk.payment.dto.invoice;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.Calendar;

@Getter
@SuperBuilder
@AnalysedEntity
public class InvoiceCallbackRequest {
    @Analysed
    private String lob;
    @Analysed
    private String customerAccountNumber;
    @Analysed
    private String invoiceId;
    @Analysed
    private String status;
    @Analysed
    private String type;
    @Analysed
    private String description;
    @Analysed
    private String skipDelivery;
}
