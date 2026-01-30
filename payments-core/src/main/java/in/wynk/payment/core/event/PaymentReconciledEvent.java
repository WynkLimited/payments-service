package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.EnumSet;

@Getter
@SuperBuilder
@AnalysedEntity
public abstract class PaymentReconciledEvent {

    @Analysed
    private final double amount;

    @Analysed
    private final Integer planId;

    @Analysed
    private final String uid;
    @Analysed
    private final String msisdn;
    @Analysed
    private final String itemId;
    @Analysed
    private final String clientAlias;
    @Analysed(name = BaseConstants.TRANSACTION_ID)
    private final String transactionId;

    @Analysed(name = BaseConstants.PAYMENT_CODE)
    private final PaymentGateway paymentGateway;

    @Analysed(name = BaseConstants.TRANSACTION_STATUS)
    private final TransactionStatus transactionStatus;

    public static PaymentReconciledEvent from(Transaction transaction) {
        final PaymentReconciledEvent paymentReconciledEvent;
        if (EnumSet.of(PaymentEvent.REFUND).contains(transaction.getType())) {
            paymentReconciledEvent = PaymentRefundReconciledEvent.from(transaction);
        } else {
            paymentReconciledEvent = PaymentChargingReconciledEvent.from(transaction);
        }
        return paymentReconciledEvent;
    }

    @Analysed(name = BaseConstants.PAYMENT_EVENT)
    public abstract PaymentEvent getPaymentEvent();

}