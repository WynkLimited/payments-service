package in.wynk.payment.core.dto;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Transaction;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@WynkQueue(queueName ="payment.pooling.queue.charging.name", delaySeconds = "payment.pooling.queue.charging.sqs.producer.delayInSecond")
public class PaymentRenewalChargingMessage{

    PaymentCode paymentCode;
    Transaction transaction;

    public PaymentRenewalChargingMessage(Transaction transaction){
        this.transaction = transaction;
        this.paymentCode = transaction.getPaymentChannel();
    }

}
