package in.wynk.payment.dto;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.queue.dto.WynkQueue;
import lombok.Builder;
import lombok.Getter;


@Getter
@Builder
@WynkQueue(queueName = "${payment.pooling.queue.renewal.name}", delaySeconds = "${payment.pooling.queue.renewal.sqs.producer.delayInSecond}")
public class PaymentRenewalMessage {

    private final String transactionId;
    private final PaymentEvent paymentEvent;

}
