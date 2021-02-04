package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.queue.dto.WynkQueue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@WynkQueue(queueName = "${payment.pooling.queue.renewal.name}", delaySeconds = "${payment.pooling.queue.renewal.sqs.producer.delayInSecond}")
public class PaymentRenewalMessage {

    @Analysed
    private int attemptSequence;
    @Analysed
    private String transactionId;
    @Analysed
    private PaymentEvent paymentEvent;

}
