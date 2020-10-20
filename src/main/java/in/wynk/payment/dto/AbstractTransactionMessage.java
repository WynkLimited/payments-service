package in.wynk.payment.dto;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.Data;

@Data
public abstract class AbstractTransactionMessage {
    private String uid;
    private String msisdn;
    private Integer planId;
    private String transactionId;
    private PaymentEvent paymentEvent;
    private PaymentCode paymentCode;
}
