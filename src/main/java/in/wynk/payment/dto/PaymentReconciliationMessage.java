package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.event.AbstractPaymentMessageThresholdExceedEvent;
import in.wynk.payment.event.PaymentRefundReconciliationMessageThresholdExceedEvent;
import in.wynk.queue.dto.MessageToEventMapper;
import in.wynk.queue.dto.QueueType;
import in.wynk.queue.dto.WynkQueue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import java.util.EnumSet;

@Getter
@SuperBuilder
@AnalysedEntity
@AllArgsConstructor
@WynkQueue(queueName = "${payment.pooling.queue.reconciliation.name}", delaySeconds = "${payment.pooling.queue.reconciliation.sqs.producer.delayInSecond}", queueType = QueueType.STANDARD)
public class PaymentReconciliationMessage extends AbstractTransactionMessage implements MessageToEventMapper<AbstractPaymentMessageThresholdExceedEvent> {
    @Override
    public AbstractPaymentMessageThresholdExceedEvent map() {
        final AbstractPaymentMessageThresholdExceedEvent event;
        final WynkQueue queueData = this.getClass().getAnnotation(WynkQueue.class);
        if (EnumSet.of(PaymentEvent.REFUND).contains(getPaymentEvent())) {
            event = PaymentRefundReconciliationMessageThresholdExceedEvent.builder()
        } else {

        }
        return null;
    }
}