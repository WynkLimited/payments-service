package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.pubsub.dto.WynkPubSub;
import in.wynk.queue.dto.WynkQueue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
//@WynkQueue(queueName = "${payment.pooling.queue.refund.name}", delaySeconds = "${payment.pooling.queue.refund.sqs.producer.delayInSecond}")
@WynkPubSub(projectName = "${payments.pooling.pubSub.refund.projectName}", topicName = "${payments.pooling.pubSub.refund.topicName}", subscriptionName = "${payments.pooling.pubSub.refund.subscriptionName}", bufferInterval = "${payments.pooling.pubSub.refund.bufferInterval}")
public class PaymentRefundInitMessage {

    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);
    @NotBlank
    @Analysed
    private String reason;
    @NotBlank
    @Analysed
    private String originalTransactionId;

}