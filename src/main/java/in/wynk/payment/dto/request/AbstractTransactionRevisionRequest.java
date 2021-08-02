package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Getter;
import lombok.experimental.SuperBuilder;


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

    public abstract int getAttemptSequence();

}
