package in.wynk.payment.dto;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.dto.WynkQueue;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@WynkQueue(queueName ="payment.pooling.queue.charging.name", delaySeconds = "payment.pooling.queue.charging.sqs.producer.delayInSecond", queueType = "FIFO")
public class PaymentRenewalChargingMessage{

    PaymentCode paymentCode;
    PaymentRenewalRequest paymentRenewalRequest;

    public PaymentRenewalChargingMessage(Transaction transaction){
        this.paymentRenewalRequest = new PaymentRenewalRequest(transaction);
        this.paymentCode = transaction.getPaymentChannel();
    }

}
