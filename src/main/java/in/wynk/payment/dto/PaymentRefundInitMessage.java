package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
//@WynkQueue(queueName = "${payment.pooling.queue.refund.name}", delaySeconds = "${payment.pooling.queue.refund.sqs.producer.delayInSecond}")
public class PaymentRefundInitMessage {

    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);
    @NotBlank
    @Analysed
    private String reason;
    @NotBlank
    @Analysed
    private String originalTransactionId;

}