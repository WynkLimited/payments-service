package in.wynk.payment.dto;

import in.wynk.payment.core.dao.entity.Transaction;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentReconciliationMessage extends AbstractTransactionMessage {

    public PaymentReconciliationMessage(Transaction transaction) {
        super.setUid(transaction.getUid());
        super.setMsisdn(transaction.getMsisdn());
        super.setPaymentCode(transaction.getPaymentChannel());
        super.setPlanId(transaction.getPlanId());
        super.setTransactionId(transaction.getId().toString());
        super.setTransactionEvent(transaction.getType());
    }
}
