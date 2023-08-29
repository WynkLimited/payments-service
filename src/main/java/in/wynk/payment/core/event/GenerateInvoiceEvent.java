package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
public class GenerateInvoiceEvent {
    @Analysed
    private String invoiceId;
    @Analysed
    private String msisdn;
    @Analysed
    private String txnId;
    @Analysed
    private String clientAlias;
}
