package in.wynk.payment.dto.gpbs.acknowledge.queue;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.payment.core.event.ExternalTransactionReportMessageThresholdEvent;
import in.wynk.queue.dto.MessageToEventMapper;

import lombok.*;
import lombok.experimental.SuperBuilder;


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
//@WynkQueue(queueName = "${payment.pooling.queue.externalTransaction.report.name}", producerType = ProducerType.QUARTZ_MESSAGE_PRODUCER, quartz = @WynkQueue.QuartzConfiguration(expression = "T(java.util.Arrays).asList(60, 60, 60, 60, 120, 300, 300, 300, 890, 890, 2400, 3600, 79200, 172800, 179800).get(#n)", publishUntil = 1, publishUntilUnit = TimeUnit.DAYS))
public class ExternalTransactionReportMessageManager extends AbstractExternalTransactionReportMessage implements MessageToEventMapper<ExternalTransactionReportMessageThresholdEvent> {
    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);

    @Override
    public ExternalTransactionReportMessageThresholdEvent map () {
        return ExternalTransactionReportMessageThresholdEvent.builder().transactionId(getTransactionId()).externalTransactionId(getExternalTransactionId()).paymentEvent(this.getPaymentEvent())
                .initialTransactionId(getInitialTransactionId())
                .build();
    }
}
