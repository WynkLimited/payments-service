package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.queue.dto.MessageToEventMapper;
import in.wynk.scheduler.queue.dto.IQueueMessage;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;


import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
//@WynkQueue(queueName = "${payment.pooling.queue.reconciliation.name}", producerType = ProducerType.ENTITY_DRIVEN_QUARTZ_MESSAGE_PUBLISHER, quartz = @WynkQueue.QuartzConfiguration(entityCacheName= PAYMENT_METHOD, publishUntil = 3, publishUntilUnit = TimeUnit.DAYS))
public class PaymentReconciliationMessage extends AbstractTransactionMessage implements MessageToEventMapper<PaymentReconciliationThresholdExceedEvent>, IQueueMessage<String> {

   @Analysed
   private String paymentMethodId;

    @Analysed
    private String extTxnId;
    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);

    @Analysed
    private String originalTransactionId;

    @Analysed
    private int originalAttemptSequence;

    @Override
    public String getEntityId () {
        return getPaymentMethodId();
    }

    @Override
    public PaymentReconciliationThresholdExceedEvent map() {
        return PaymentReconciliationThresholdExceedEvent.builder()
                .paymentMethodId(getPaymentMethodId())
                .uid(getUid())
                .planId(getPlanId())
                .itemId(getItemId())
                .msisdn(getMsisdn())
                .extTxnId(getExtTxnId())
                .clientAlias(getClientAlias())
                .paymentEvent(getPaymentEvent())
                .transactionId(getTransactionId())
                .paymentGateway(PaymentCodeCachingService.getFromPaymentCode(getPaymentCode()))
                .build();
    }
}