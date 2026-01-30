package in.wynk.payment.presentation.dto.status;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.PaymentGroup;
import in.wynk.payment.dto.AbstractPack;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public abstract class PaymentStatusResponse {

    private final int planId;
    private final String itemId;
    private final String tid;
    private final TransactionStatus transactionStatus;
    private final PaymentEvent transactionType;
    private final String redirectUrl;
    private final AbstractPack packDetails;
    private final String paymentGroup;
}
