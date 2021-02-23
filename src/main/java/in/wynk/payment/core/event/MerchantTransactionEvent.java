package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@AnalysedEntity
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class MerchantTransactionEvent {
    @Analysed(name = BaseConstants.TRANSACTION_ID)
    private final String id;
    @Analysed
    private final String externalTransactionId;
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
        private Object request;
        private Object response;

        private Builder(String transactionId) {
            this.transactionId = transactionId;
        }

        public Builder externalTransactionId(String externalTransactionId) {
            this.externalTransactionId = externalTransactionId;
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
            return new MerchantTransactionEvent(transactionId, externalTransactionId, request, response);
        }

    }

}
