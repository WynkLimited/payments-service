package in.wynk.payment.core.dto;

import in.wynk.commons.enums.TransactionEvent;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.Data;

@Data
public abstract class AbstractTransactionMessage {
    private String uid;
    private String msisdn;
    private Integer planId;
    private String transactionId;
    private TransactionEvent transactionEvent;
    private PaymentCode paymentCode;
}
