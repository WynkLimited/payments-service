package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
@AnalysedEntity
public class InvoiceRetryEvent {
    @Analysed
    private String invoiceId;
    @Analysed
    private String msisdn;
    @Analysed
    private String txnId;
    @Analysed
    private String clientAlias;
    @Analysed
    private List<Long> retries;
}