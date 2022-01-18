package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.event.PaymentRenewalMessageThresholdExceedEvent;
import in.wynk.queue.dto.MessageToEventMapper;
import in.wynk.queue.dto.WynkQueue;
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
@WynkQueue(queueName = "${payment.pooling.queue.renewal.name}", delaySeconds = "${payment.pooling.queue.renewal.sqs.producer.delayInSecond}")
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
        WynkQueue queueData = this.getClass().getAnnotation(WynkQueue.class);
        return PaymentRenewalMessageThresholdExceedEvent.builder()
                .maxAttempt(queueData.maxRetryCount())
                .attemptSequence(getAttemptSequence())
                .transactionId(getTransactionId())
                .clientAlias(getClientAlias())
                .build();
    }

}