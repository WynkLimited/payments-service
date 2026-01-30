package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class PaymentRefundReconciledEvent extends PaymentReconciledEvent {
    @Override
    public PaymentEvent getPaymentEvent() {
        return PaymentEvent.REFUND;
    }

    public static PaymentRefundReconciledEvent from(Transaction transaction) {
        return PaymentRefundReconciledEvent.builder()
                .paymentGateway(transaction.getPaymentChannel())
                .transactionStatus(transaction.getStatus())
                .clientAlias(transaction.getClientAlias())
                .transactionId(transaction.getIdStr())
                .msisdn(transaction.getMsisdn())
                .itemId(transaction.getItemId())
                .planId(transaction.getPlanId())
                .amount(transaction.getAmount())
                .uid(transaction.getUid())
                .build();
    }
}
