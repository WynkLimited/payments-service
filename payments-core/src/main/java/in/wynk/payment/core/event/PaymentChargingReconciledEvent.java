package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class PaymentChargingReconciledEvent extends PaymentReconciledEvent {

    private final PaymentEvent paymentEvent;

    @Override
    public PaymentEvent getPaymentEvent() {
        return paymentEvent;
    }

    public static PaymentChargingReconciledEvent from(Transaction transaction) {
        return PaymentChargingReconciledEvent.builder()
                .paymentGateway(transaction.getPaymentChannel())
                .transactionStatus(transaction.getStatus())
                .clientAlias(transaction.getClientAlias())
                .transactionId(transaction.getIdStr())
                .paymentEvent(transaction.getType())
                .msisdn(transaction.getMsisdn())
                .itemId(transaction.getItemId())
                .planId(transaction.getPlanId())
                .amount(transaction.getAmount())
                .uid(transaction.getUid())
                .build();
    }
}
