package in.wynk.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter
public class ConsultBalanceRequest {

    private ConsultBalanceRequestHead head;

    private ConsultBalanceRequestBody body;

    public static interface HeadStep {
        BodyStep withHead(ConsultBalanceRequestHead head);
    }

    public static interface BodyStep {
        BuildStep withBody(ConsultBalanceRequestBody body);
    }

    public static interface BuildStep {
        ConsultBalanceRequest build();
    }

    public static class Builder implements HeadStep, BodyStep, BuildStep {
        private ConsultBalanceRequestHead head;
        private ConsultBalanceRequestBody body;

        private Builder() {
        }

        public static HeadStep consultBalanceRequest() {
            return new Builder();
        }

        @Override
        public BodyStep withHead(ConsultBalanceRequestHead head) {
            this.head = head;
            return this;
        }

        @Override
        public BuildStep withBody(ConsultBalanceRequestBody body) {
            this.body = body;
            return this;
        }

        @Override
        public ConsultBalanceRequest build() {
            return new ConsultBalanceRequest(
                    this.head,
                    this.body
            );
        }
    }

    @Override
    public String toString() {
        return "ConsultBalanceRequest{" +
                "head=" + head +
                ", body=" + body +
                '}';
    };

}
