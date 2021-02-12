package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.queue.dto.FIFOQueueMessageMarker;
import in.wynk.queue.dto.QueueType;
import in.wynk.queue.dto.WynkQueue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@WynkQueue(queueName = "${payment.pooling.queue.charging.name}", delaySeconds = "${payment.pooling.queue.charging.sqs.producer.delayInSecond}", queueType = QueueType.FIFO)
public class PaymentRenewalChargingMessage implements FIFOQueueMessageMarker {

    @Analysed(name = "old_transaction_id")
    private String id;
    @Analysed
    private String uid;
    @Analysed
    private String msisdn;
    @Analysed
    private Integer planId;
    @Analysed
    private int attemptSequence;
    @Analysed
    private String clientAlias;
    @Analysed
    private PaymentCode paymentCode;

    @Override
    public String getMessageGroupId() {
        return paymentCode.getCode();
    }

    @Override
    public String getMessageDeDuplicationId() {
        return id;
    }

}
