package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.StatusMode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public abstract class AbstractTransactionReconciliationStatusRequest extends AbstractTransactionStatusRequest {

    @Analysed
    private final String extTxnId;

    @Override
    public StatusMode getMode() {
        return StatusMode.SOURCE;
    }

}
