package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.auth.dao.entity.Client;
import in.wynk.client.context.ClientContext;
import in.wynk.common.constant.BaseConstants;
import in.wynk.stream.advice.KafkaEvent;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import static in.wynk.payment.core.constant.PaymentConstants.PAYMENT_API_CLIENT;

@Getter
@AnalysedEntity
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@KafkaEvent(topic = "${wynk.data.platform.topic}", transactionName = "paymentErrorEvent")
public class PaymentErrorEvent {

    @Analysed(name = BaseConstants.TRANSACTION_ID)
    private final String id;
    @Analysed
    private final String code;
    @Analysed
    private final String description;
    @Analysed
    private final String clientAlias;

    public static Builder builder(String transactionId) {
        return new Builder(transactionId);
    }

    public static class Builder {

        private final String transactionId;
        private String code;
        private String description;
        private String clientAlias;

        private Builder(String transactionId) {
            this.transactionId = transactionId;
        }

        public PaymentErrorEvent build() {
            return new PaymentErrorEvent(transactionId, code, description, ClientContext.getClient().map(Client::getAlias).orElse(PAYMENT_API_CLIENT));
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

        public Builder clientAlias(String clientAlias) {
            this.clientAlias = clientAlias;
            return this;
        }
    }
}