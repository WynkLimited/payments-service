package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.payment.core.event.GooglePlayMessageThresholdEvent;
import in.wynk.queue.dto.MessageToEventMapper;
import in.wynk.queue.dto.ProducerType;
import in.wynk.queue.dto.WynkQueue;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.util.concurrent.TimeUnit;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
@AnalysedEntity
@WynkQueue(queueName = "${payment.pooling.queue.acknowledgement.name}", producerType = ProducerType.QUARTZ_MESSAGE_PRODUCER, quartz = @WynkQueue.QuartzConfiguration(expression = "T(java.util.Arrays).asList(60, 60, 60, 60, 60, 300, 300, 300, 890, 890, 2400, 3600, 79200, 172800, 179800).get(#n)",publishUntil  = 3, publishUntilUnit = TimeUnit.DAYS))
public class GoogleAcknowledgeMessageManager extends AbstractTransactionMessage implements MessageToEventMapper<GooglePlayMessageThresholdEvent> {
    @Analysed
    private String packageName;

    @Analysed
    private String service;

    @Analysed
    private String purchaseToken;

    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);

    @Analysed
    private String developerPayload;

    @Analysed
    private String skuId;

    @Override
    public GooglePlayMessageThresholdEvent map () {
        return GooglePlayMessageThresholdEvent.builder()
                .developerPayload(developerPayload)
                .service(service)
                .packageName(packageName)
                .purchaseToken(purchaseToken)
                .skuId(skuId)
                .build();
    }
}
