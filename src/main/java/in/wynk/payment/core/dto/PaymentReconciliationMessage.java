package in.wynk.payment.core.dto;

import in.wynk.commons.enums.TransactionEvent;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentReconciliationMessage extends AbstractTransactionMessage {
    private String externalTransactionId;
    private long initTimestamp;


    public Date getInitTimestamp() {
        return new Date(initTimestamp);
    }

    public PaymentReconciliationMessage(Transaction transaction){
        super.setUid(transaction.getUid());
        super.setMsisdn(transaction.getMsisdn());
        super.setPaymentCode(PaymentCode.valueOf(transaction.getPaymentChannel()));
        super.setPlanId(transaction.getPlanId());
        super.setTransactionId(transaction.getId().toString());
        super.setTransactionEvent(TransactionEvent.valueOf(transaction.getType()));
        this.externalTransactionId = transaction.getMerchantTransaction().getExternalTransactionId();
        this.initTimestamp = System.currentTimeMillis();
    }
}
