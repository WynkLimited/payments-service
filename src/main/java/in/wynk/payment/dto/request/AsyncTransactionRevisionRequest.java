package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@ToString
public class AsyncTransactionRevisionRequest extends AbstractTransactionRevisionRequest {

    @Analysed
    private final int attemptSequence;

    @Analysed
    private final String originalTransactionId;

}