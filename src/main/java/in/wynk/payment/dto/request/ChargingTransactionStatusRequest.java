package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.StatusMode;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class ChargingTransactionStatusRequest extends AbstractTransactionStatusRequest {

    @Override
    public StatusMode getMode() {
        return StatusMode.LOCAL;
    }

}
