package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.dao.entity.PaymentTDRDetails;
import in.wynk.payment.core.event.PaymentRenewalMessageThresholdExceedEvent;
import in.wynk.payment.core.event.PreDebitNotificationMessageThresholdEvent;
import in.wynk.payment.core.event.TdrProcessingMessageThresholdExceedEvent;
import in.wynk.stream.advice.WynkKafkaMessage;
import in.wynk.stream.dto.MessageToEventMapper;
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
@WynkKafkaMessage(topic = "${wynk.kafka.consumers.listenerFactory.tdrProcessing[0].factoryDetails.topic}")
public class TdrProcessingMessage implements MessageToEventMapper<TdrProcessingMessageThresholdExceedEvent> {

    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);

    @Analysed
    private String transactionId;

    @Analysed
    private PaymentTDRDetails paymentTDRDetails;


    @Override
    public TdrProcessingMessageThresholdExceedEvent map() {
        WynkKafkaMessage kafkaMessage = this.getClass().getAnnotation(WynkKafkaMessage.class);
        return TdrProcessingMessageThresholdExceedEvent.builder()
                .maxAttempt(kafkaMessage.maxRetryCount())
                .transactionId(getTransactionId())
                .clientAlias(getClientAlias())
                .build();
    }

}