package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import static in.wynk.exception.WynkErrorType.UT025;

@Getter
@SuperBuilder
@AnalysedEntity
public abstract class AbstractTransactionRevisionRequest {

    @Analysed
    private final Transaction transaction;

    @Analysed
    private final TransactionStatus finalTransactionStatus;

    @Analysed
    private final TransactionStatus existingTransactionStatus;

    @Analysed
    @Setter
    private String lastSuccessTransactionId;

    @Analysed
    private boolean retryForAps;

    public int getAttemptSequence() {
        return 0;
    };

    public String getOriginalTransactionId () {
        throw new WynkRuntimeException(UT025);
    }

}