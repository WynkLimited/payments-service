package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class DefaultPaymentSettlementResponse extends AbstractPaymentSettlementResponse {
    @Analysed
    private String referenceId;
}
