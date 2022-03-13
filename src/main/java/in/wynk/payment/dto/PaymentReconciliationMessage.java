package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.queue.dto.MessageToEventMapper;
import in.wynk.queue.dto.ProducerType;
import in.wynk.queue.dto.WynkQueue;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.util.concurrent.TimeUnit;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@WynkQueue(queueName = "${payment.pooling.queue.reconciliation.name}", producerType = ProducerType.QUARTZ_MESSAGE_PRODUCER, quartz = @WynkQueue.QuartzConfiguration(expression = "T(java.util.Arrays).asList(60, 60, 60, 60, 60, 300, 300, 300, 890, 890, 2400, 3600, 79200, 172800, 179800).get(#n)", publishUntil = 3, publishUntilUnit = TimeUnit.DAYS))
public class PaymentReconciliationMessage extends AbstractTransactionMessage implements MessageToEventMapper<PaymentReconciliationThresholdExceedEvent> {

    @Analysed
    private String extTxnId;
    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);

    @Analysed
    private String originalTransactionId;

    @Analysed
    private int originalAttemptSequence;

    @Override
    public PaymentReconciliationThresholdExceedEvent map() {
        return PaymentReconciliationThresholdExceedEvent.builder()
                .uid(getUid())
                .planId(getPlanId())
                .itemId(getItemId())
                .msisdn(getMsisdn())
                .extTxnId(getExtTxnId())
                .clientAlias(getClientAlias())
                .paymentEvent(getPaymentEvent())
                .transactionId(getTransactionId())
                .paymentCode(PaymentCodeCachingService.getFromPaymentCode(getPaymentCode()))
                .build();
    }

}