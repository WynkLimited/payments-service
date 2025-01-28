package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AnalysedEntity
@RequiredArgsConstructor
public class InvoiceRetryTaskEvent {
    @Analysed
    private final String msisdn;
    @Analysed
    private final String transactionId;
    @Analysed
    private final String clientAlias;
    @Analysed
    private final String type;
    @Analysed
    private final String skipDelivery;
    @Analysed
    private final int retryCount;
}