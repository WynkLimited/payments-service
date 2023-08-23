package in.wynk.payment.dto.invoice;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class GenerateInvoiceRequest {
    @Analysed
    private final String msisdn;
    @Analysed
    private final Transaction transaction;
    @Analysed
    private final IPurchaseDetails purchaseDetails;
}
