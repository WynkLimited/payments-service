package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.queue.dto.QueueType;
import in.wynk.queue.dto.WynkQueue;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@WynkQueue(queueName = "${payment.pooling.queue.reconciliation.name}", delaySeconds = "${payment.pooling.queue.reconciliation.sqs.producer.delayInSecond}", queueType = QueueType.STANDARD)
public class PaymentReconciliationMessage extends AbstractTransactionMessage {

    @Analysed
    private String extTxnId;

}