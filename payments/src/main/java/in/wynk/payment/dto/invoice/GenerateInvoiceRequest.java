package in.wynk.payment.dto.invoice;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class GenerateInvoiceRequest {
    @Analysed
    private final String msisdn;
    @Analysed
    private final String clientAlias;
    @Analysed
    private final String txnId;
    @Analysed
    private final String type;
    @Analysed
    private String skipDelivery;
}
