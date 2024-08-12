package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.stream.advice.WynkKafkaMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@WynkKafkaMessage(topic = "${payment.pooling.kafka.preDebitNotification.topic}")
public class PreDebitNotificationMessage {

    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);

    @Analysed
    private String date;

    @Analysed
    private String transactionId;

}