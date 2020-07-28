package in.wynk.payment.dto;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.queue.dto.FIFOQueueMessageMarker;
import in.wynk.queue.dto.QueueType;
import in.wynk.queue.dto.WynkQueue;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@WynkQueue(queueName = "${payment.pooling.queue.charging.name}", delaySeconds = "${payment.pooling.queue.charging.sqs.producer.delayInSecond}", queueType = QueueType.FIFO)
public class PaymentRenewalChargingMessage implements FIFOQueueMessageMarker {

    private PaymentCode paymentCode;
    private PaymentRenewalRequest paymentRenewalRequest;

    @Override
    public String getMessageGroupId() {
        return paymentCode.getCode();
    }

    @Override
    public String getMessageDeDuplicationId() {
        return paymentRenewalRequest.getTransactionId();
    }

}
