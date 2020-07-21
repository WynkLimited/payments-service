package in.wynk.payment.dto;

import in.wynk.commons.enums.TransactionEvent;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dto.WynkQueue;
import lombok.*;


@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@WynkQueue(queueName ="payment.pooling.queue.renewal.name", delaySeconds = "payment.pooling.queue.renewal.sqs.producer.delayInSecond")
public class PaymentRenewalMessage{

    private String transactionId;
    private TransactionEvent transactionEvent;

    public PaymentRenewalMessage(PaymentRenewal paymentRenewal){
        this.transactionId = paymentRenewal.getTransactionId();
        this.transactionEvent = paymentRenewal.getTransactionEvent();
    }

}
