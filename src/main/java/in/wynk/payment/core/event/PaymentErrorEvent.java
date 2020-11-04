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
public class PaymentErrorEvent {
    @Analysed(name = BaseConstants.TRANSACTION_ID)
    private final String id;
    @Analysed
    private final String code;
    @Analysed
    private final String description;

    public static Builder builder(String transactionId) {
        return new Builder(transactionId);
    }

    public static class Builder {

        private final String transactionId;
        private String code;
        private String description;

        private Builder(String transactionId) {
            this.transactionId = transactionId;
        }

        public PaymentErrorEvent build() {
            return new PaymentErrorEvent(transactionId, code, description);
        }

        public Builder code(String code) {
            this.code = code;
            return this;
        }

        public Builder description(String description) {
            this.description = description;
            return this;
        }

    }
}
