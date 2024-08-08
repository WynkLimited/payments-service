package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.event.PaymentRenewalMessageThresholdExceedEvent;
import in.wynk.pubsub.dto.WynkPubSub;
import in.wynk.queue.dto.MessageToEventMapper;
import in.wynk.stream.advice.WynkKafkaMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
//@WynkQueue(queueName = "${payment.pooling.queue.renewal.name}", delaySeconds = "${payment.pooling.queue.renewal.sqs.producer.delayInSecond}")
//@WynkPubSub(projectName = "${payments.pooling.pubSub.renewal.projectName}", topicName = "${payments.pooling.pubSub.renewal.topicName}", subscriptionName = "${payments.pooling.pubSub.renewal.subscriptionName}", bufferInterval = "${payments.pooling.pubSub.renewal.bufferInterval}")
@WynkKafkaMessage(topic = "${wynk.kafka.consumers.listenerFactory.paymentRenewal[0].factoryDetails.topic}")
public class PaymentRenewalMessage implements MessageToEventMapper<PaymentRenewalMessageThresholdExceedEvent> {

    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);
    @Analysed
    private int attemptSequence;
    @Analysed
    private String transactionId;
    @Analysed
    private PaymentEvent paymentEvent;

    @Override
    public PaymentRenewalMessageThresholdExceedEvent map() {
        WynkPubSub pubSubData = this.getClass().getAnnotation(WynkPubSub.class);
        return PaymentRenewalMessageThresholdExceedEvent.builder()
                .maxAttempt(pubSubData.maxRetryCount())
                .attemptSequence(getAttemptSequence())
                .transactionId(getTransactionId())
                .clientAlias(getClientAlias())
                .build();
    }

}