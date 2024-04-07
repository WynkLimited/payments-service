package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.pubsub.dto.WynkPubSub;
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
@WynkPubSub(enabled = true, topicName = "projects/prj-wynk-stg-wcf-svc-01/topics/wcf-starter-poc", subscriptionName = "projects/prj-wynk-stg-wcf-svc-01/subscriptions/wcf-starter-poc-sub",projectName = "prj-wynk-stg-wcf-svc-01")
public class PaymentReconciliationMessagePubSub {

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


}
