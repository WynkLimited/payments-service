package in.wynk.payment.core.utils;

import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@SuperBuilder
@NoArgsConstructor
public abstract class AbstractPaymentClientAlias {
    @Builder.Default
    private final String clientAlias = ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT);
}