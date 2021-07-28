package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class SyncTransactionRevisionRequest extends AbstractTransactionRevisionRequest {

    @Override
    public int getAttemptSequence() {
        return 0;
    }

    @Override
    public String getTransactionId() {
        return null;
    }

}