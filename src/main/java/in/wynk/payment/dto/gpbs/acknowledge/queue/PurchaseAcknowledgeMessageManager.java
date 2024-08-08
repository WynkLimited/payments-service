package in.wynk.payment.dto.gpbs.acknowledge.queue;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.payment.core.event.PurchaseAcknowledgementMessageThresholdEvent;
import in.wynk.stream.advice.WynkKafkaMessage;
import in.wynk.stream.constant.ProducerType;
import in.wynk.stream.dto.MessageToEventMapper;
import lombok.*;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.TimeUnit;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
@AllArgsConstructor
@AnalysedEntity
//@WynkQueue(queueName = "${payment.pooling.queue.acknowledgement.name}", producerType = ProducerType.QUARTZ_MESSAGE_PRODUCER, quartz = @WynkQueue.QuartzConfiguration(expression = "T(java.util.Arrays).asList(60, 60, 60, 60, 120, 300, 300, 300, 890, 890, 2400, 3600, 79200, 172800, 179800).get(#n)", publishUntil = 1, publishUntilUnit = TimeUnit.DAYS))
//@WynkPubSub(projectName = "${payments.pooling.pubSub.acknowledgement.projectName}", topicName = "${payments.pooling.pubSub.acknowledgement.topicName}", subscriptionName = "${payments.pooling.pubSub.acknowledgement.subscriptionName}", bufferInterval = "${payments.pooling.pubSub.acknowledgement.bufferInterval}", producerType = GCPProducerType.QUARTZ_MESSAGE_PRODUCER, quartz = @WynkPubSub.QuartzConfigurationGCP(expression = "T(java.util.Arrays).asList(60, 60, 60, 60, 120, 300, 300, 300, 890, 890, 2400, 3600, 79200, 172800, 179800).get(#n)", publishUntil = 1, publishUntilUnit = TimeUnit.DAYS))
@WynkKafkaMessage(topic = "${wynk.kafka.consumers.listenerFactory.purchaseAcknowledgement[0].factoryDetails.topic}", producerType = ProducerType.QUARTZ_MESSAGE_PRODUCER, quartz = @WynkKafkaMessage.QuartzConfiguration(expression = "T(java.util.Arrays).asList(60, 60, 60, 60, 120, 300, 300, 300, 890, 890, 2400, 3600, 79200, 172800, 179800).get(#n)", publishUntil = 1, publishUntilUnit = TimeUnit.DAYS))
public class PurchaseAcknowledgeMessageManager extends AbstractAcknowledgementMessage implements MessageToEventMapper<PurchaseAcknowledgementMessageThresholdEvent> {

    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);

    @Override
    public PurchaseAcknowledgementMessageThresholdEvent map () {
        return PurchaseAcknowledgementMessageThresholdEvent.builder()
                .developerPayload(getDeveloperPayload())
                .service(getService())
                .packageName(getService())
                .purchaseToken(getPurchaseToken())
                .skuId(getSkuId())
                .paymentCode(this.getPaymentCode())
                .type(getType())
                .txnId(getTxnId())
                .build();
    }
}
