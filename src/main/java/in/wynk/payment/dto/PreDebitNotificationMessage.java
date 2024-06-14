package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.queue.dto.ProducerType;
import in.wynk.queue.dto.WynkQueue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Temporal;
import javax.persistence.TemporalType;
import java.util.Calendar;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@WynkQueue(queueName = "${payment.pooling.queue.preDebitNotification.name}", producerType = ProducerType.QUARTZ_MESSAGE_PRODUCER,
        quartz = @WynkQueue.QuartzConfiguration(expression = "T(java.util.Arrays).asList(20, 1800, 2100, 2700, 28800, 36000, 36000, 43200).get(#n)", publishUntil = 2,
                publishUntilUnit = TimeUnit.DAYS))
public class PreDebitNotificationMessage {

    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);

    @Temporal(TemporalType.DATE)
    @Analysed
    private Calendar renewalDay;

    @Temporal(TemporalType.TIME)
    @Analysed
    private Date renewalHour;

    @Analysed
    private String transactionId;

    @Analysed
    private String initialTransactionId;

    @Analysed
    private String lastSuccessTransactionId;
}