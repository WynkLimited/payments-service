package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.stream.advice.WynkKafkaMessage;
import in.wynk.stream.constant.ProducerType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.concurrent.TimeUnit;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
//@WynkKafkaMessage(topic = "${payment.pooling.kafka.preDebitNotification.topic}")
@WynkKafkaMessage(topic = "${wynk.kafka.consumers.listenerFactory.preDebitNotification[0].factoryDetails.topic}", producerType = ProducerType.QUARTZ_MESSAGE_PRODUCER,
        quartz = @WynkKafkaMessage.QuartzConfiguration(expression = "T(java.util.Arrays).asList(60, 240, 1500, 1800, 18000, 64800, 21600, 21600).get(#n)", publishUntil = 2,
                publishUntilUnit = TimeUnit.DAYS))
public class PreDebitNotificationMessage {

    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);

    @Analysed
    private String date;

    @Analysed
    private String transactionId;

}