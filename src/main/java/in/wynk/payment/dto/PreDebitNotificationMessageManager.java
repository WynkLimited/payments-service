package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.payment.core.event.PreDebitNotificationMessageThresholdEvent;
import in.wynk.queue.dto.MessageToEventMapper;
import in.wynk.queue.dto.ProducerType;
import in.wynk.queue.dto.WynkQueue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.TimeUnit;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@WynkQueue(queueName = "${payment.pooling.queue.preDebitNotification.name}", producerType = ProducerType.QUARTZ_MESSAGE_PRODUCER,
        quartz = @WynkQueue.QuartzConfiguration(expression = "T(java.util.Arrays).asList(60, 240, 1500, 1800, 18000, 64800, 21600, 21600).get(#n)", publishUntil = 2,
                publishUntilUnit = TimeUnit.DAYS))
public class PreDebitNotificationMessageManager extends AbstractPreDebitNotificationMessage implements MessageToEventMapper<PreDebitNotificationMessageThresholdEvent> {

    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);

    @Override
    public PreDebitNotificationMessageThresholdEvent map () {
        return PreDebitNotificationMessageThresholdEvent.builder().transactionId(getTransactionId()).build();
    }
}