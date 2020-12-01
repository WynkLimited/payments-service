package in.wynk.payment.dto;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.queue.dto.WynkQueue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@WynkQueue(queueName = "${payment.pooling.queue.renewal.name}", delaySeconds = "${payment.pooling.queue.renewal.sqs.producer.delayInSecond}")
public class PaymentRenewalMessage {

    private String transactionId;
    private PaymentEvent paymentEvent;

}
