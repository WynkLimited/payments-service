package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.constant.BaseConstants;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@AnalysedEntity
@ToString
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MerchantTransactionEvent {

    @Analysed(name = BaseConstants.TRANSACTION_ID)
    private final String id;
    @Analysed
    private final String clientAlias;
    @Analysed
    private final String externalTransactionId;
    @Analysed
    private final String orderId;
    @Analysed
    private final String externalTokenReferenceId;
    @Analysed
    private final Object request;
    @Analysed
    private final Object response;

    public static Builder builder(String transactionId) {
        return new Builder(transactionId);
    }

    public static class Builder {

        private final String transactionId;
        private String externalTransactionId;
        private String orderId;
        private String externalTokenReferenceId;
        private Object response;
        private Object request;

        private Builder(String transactionId) {
            this.transactionId = transactionId;
        }

        public Builder externalTransactionId(String externalTransactionId) {
            this.externalTransactionId = externalTransactionId;
            return this;
        }

        public Builder orderId(String orderId) {
            this.orderId = orderId;
            return this;
        }

        public Builder externalTokenReferenceId(String externalTokenReferenceId) {
            this.externalTokenReferenceId = externalTokenReferenceId;
            return this;
        }

        public Builder request(Object request) {
            this.request = request;
            return this;
        }

        public Builder response(Object response) {
            this.response = response;
            return this;
        }

        public MerchantTransactionEvent build() {
            return new MerchantTransactionEvent(transactionId, ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT), externalTransactionId, orderId, externalTokenReferenceId, request, response);
        }
    }
}