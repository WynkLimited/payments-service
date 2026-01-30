package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import lombok.Builder;
import lombok.Getter;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@Builder
public class PaymentRefundInitEvent {

    @Analysed
    private final String reason;
    @Analysed
    private final String originalTransactionId;
    @Builder.Default
    private String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);

}