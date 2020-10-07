package in.wynk.payment.dto;

import in.wynk.common.enums.TransactionEvent;
import in.wynk.queue.dto.WynkQueue;
import lombok.*;


@Getter
@Builder
@WynkQueue(queueName ="${payment.pooling.queue.renewal.name}", delaySeconds = "${payment.pooling.queue.renewal.sqs.producer.delayInSecond}")
public class PaymentRenewalMessage{

    private String transactionId;
    private TransactionEvent transactionEvent;

}
