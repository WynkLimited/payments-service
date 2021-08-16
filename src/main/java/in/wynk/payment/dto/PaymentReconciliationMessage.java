package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.queue.dto.MessageToEventMapper;
import in.wynk.queue.dto.ProducerType;
import in.wynk.queue.dto.WynkQueue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.TimeUnit;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@WynkQueue(queueName = "${payment.pooling.queue.reconciliation.name}", producerType = ProducerType.QUARTZ_MESSAGE_PRODUCER, quartz = @WynkQueue.QuartzConfiguration(expression = "T(java.util.Arrays).asList(60, 60, 60, 60, 60, 300, 300, 300, 2400, 3600, 79200, 172800, 179800).get(#n)", publishUntil = 3, publishUntilUnit = TimeUnit.DAYS))
public class PaymentReconciliationMessage extends AbstractTransactionMessage implements MessageToEventMapper<PaymentReconciliationThresholdExceedEvent> {

    @Analysed
    private String extTxnId;

    @Override
    public PaymentReconciliationThresholdExceedEvent map() {
        return PaymentReconciliationThresholdExceedEvent.builder()
                .uid(getUid())
                .planId(getPlanId())
                .itemId(getItemId())
                .msisdn(getMsisdn())
                .extTxnId(getExtTxnId())
                .paymentCode(getPaymentCode())
                .paymentEvent(getPaymentEvent())
                .transactionId(getTransactionId())
                .build();
    }
}