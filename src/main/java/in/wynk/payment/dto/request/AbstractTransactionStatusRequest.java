package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.StatusMode;
import lombok.*;
import lombok.experimental.SuperBuilder;


@Getter
@Setter
@SuperBuilder
@AnalysedEntity
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.NONE)
public abstract class AbstractTransactionStatusRequest {

    @Analysed(name = PaymentConstants.TRANSACTION_ID)
    private String transactionId;

    @Analysed
    @Setter
    private int planId;

    @Analysed
    @Setter
    private String itemId;

    @Analysed
    public abstract StatusMode getMode();

}
