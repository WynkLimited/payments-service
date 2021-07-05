package in.wynk.payment.dto.response.payu;

import in.wynk.exception.WynkRuntimeException;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Objects;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY104;

@Getter
@NoArgsConstructor
public class PayUUpiIntentInitResponse {

    private Result result;

    @Getter
    @NoArgsConstructor
    private class Result {

        private String amount;
        private String paymentId;
        private String merchantVpa;
        private String acsTemplate;
        private String merchantName;
        private String intentURIData;

    }

    public String getDeepLink() {
        StringBuilder stringBuilder = new StringBuilder();
        if (Objects.nonNull(this.result)) {
            stringBuilder.append("upi://pay?");
            if (Objects.nonNull(this.result.intentURIData)) {
                stringBuilder.append(this.result.intentURIData);
                return stringBuilder.toString();
            }
        }
        throw new WynkRuntimeException(PAY104);
    }

}