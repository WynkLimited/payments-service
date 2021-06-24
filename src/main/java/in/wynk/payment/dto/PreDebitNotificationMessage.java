package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
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
@WynkQueue(queueName = "${payment.pooling.queue.preDebitNotification.name}", delaySeconds = "${payment.pooling.queue.preDebitNotification.sqs.producer.delayInSecond}")
public class PreDebitNotificationMessage {

    @Analysed
    private String date;

    @Analysed
    private String transactionId;

}
