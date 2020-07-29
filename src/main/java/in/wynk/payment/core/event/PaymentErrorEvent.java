package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
@AnalysedEntity
public class PaymentErrorEvent {
    private final String id;
    private final String code;
    private final String description;

    public static Builder builder(String transactionId) {
        return new Builder(transactionId);
    }

    public static class Builder {

        private String transactionId;
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
