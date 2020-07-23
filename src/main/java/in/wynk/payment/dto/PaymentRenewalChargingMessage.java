package in.wynk.payment.dto;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.queue.dto.QueueType;
import in.wynk.queue.dto.WynkQueue;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@WynkQueue(queueName ="payment.pooling.queue.charging.name", delaySeconds = "payment.pooling.queue.charging.sqs.producer.delayInSecond", queueType = QueueType.FIFO)
public class PaymentRenewalChargingMessage{

    private PaymentCode paymentCode;
    private PaymentRenewalRequest paymentRenewalRequest;

}
