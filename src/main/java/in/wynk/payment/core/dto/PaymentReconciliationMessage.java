package in.wynk.payment.core.dto;

import in.wynk.payment.core.dao.entity.Transaction;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentReconciliationMessage extends AbstractTransactionMessage {

    public PaymentReconciliationMessage(Transaction transaction){
        super.setUid(transaction.getUid());
        super.setMsisdn(transaction.getMsisdn());
        super.setPaymentCode(transaction.getPaymentChannel());
        super.setPlanId(transaction.getPlanId());
        super.setTransactionId(transaction.getId().toString());
        super.setTransactionEvent(transaction.getType());
    }
}
