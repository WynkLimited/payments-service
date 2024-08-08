package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.queue.dto.FIFOQueueMessageMarker;
import in.wynk.stream.advice.WynkKafkaMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
//@WynkQueue(queueName = "${payment.pooling.queue.charging.name}", delaySeconds = "${payment.pooling.queue.charging.sqs.producer.delayInSecond}", queueType = QueueType.FIFO)
//@WynkPubSub(projectName = "${payments.pooling.pubSub.charging.projectName}", topicName = "${payments.pooling.pubSub.charging.topicName}", subscriptionName = "${payments.pooling.pubSub.charging.subscriptionName}", bufferInterval = "${payments.pooling.pubSub.charging.bufferInterval}")
@WynkKafkaMessage(topic = "${wynk.kafka.consumers.listenerFactory.paymentRenewalCharging[0].factoryDetails.topic}")
public class PaymentRenewalChargingMessage implements FIFOQueueMessageMarker {

    @Analysed
    private int attemptSequence;

    @Analysed(name = "old_transaction_id")
    private String id;
    @Analysed
    private String uid;
    @Analysed
    private String msisdn;
    @Analysed
    private String clientAlias;
    @Analysed
    private String paymentCode;

    @Analysed
    private Integer planId;

    @Override
    public String getMessageGroupId() {
        return PaymentCodeCachingService.getFromPaymentCode(this.paymentCode).getCode();
    }

    @Override
    public String getMessageDeDuplicationId() {
        return id;
    }

}