package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import lombok.Builder;
import lombok.Getter;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@Builder
@AnalysedEntity
public class PaymentUserDeactivationEvent {
    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);
    @Analysed
    private final String id;
    @Analysed
    private final String uid;
    @Analysed
    private final String oldUid;
    @Analysed
    private String service;
}