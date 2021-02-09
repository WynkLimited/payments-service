package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AnalysedEntity
public class PaymentRefundedEvent {
    @Analysed
    private final String uid;
    @Analysed
    private final String msisdn;
    @Analysed
    private final int planId;
    @Analysed
    private final String itemId;
    @Analysed
    private final double amount;
    @Analysed
    private final String externalReferenceId;
    @Analysed
    private final String clientAlias;
    @Analysed(name = BaseConstants.TRANSACTION_ID)
    private final String transactionId;
    @Analysed(name = BaseConstants.PAYMENT_CODE)
    private final PaymentCode paymentCode;
    @Analysed(name = BaseConstants.PAYMENT_EVENT)
    private final PaymentEvent paymentEvent;
    @Analysed(name = BaseConstants.TRANSACTION_STATUS)
    private final TransactionStatus transactionStatus;

}
