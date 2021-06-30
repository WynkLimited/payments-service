package in.wynk.payment.dto.response.payu;

import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Objects;

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
            } else {
                stringBuilder.append("pa=");
                if (StringUtils.isNotBlank(this.result.merchantVpa)) {
                    stringBuilder.append(this.result.merchantVpa);
                }
                stringBuilder.append("&pn=WynkLimited&tr=");
                if (StringUtils.isNotBlank(this.result.paymentId)) {
                    stringBuilder.append(this.result.paymentId);
                }
                stringBuilder.append("&am=");
                if (StringUtils.isNotBlank(this.result.amount)) {
                    stringBuilder.append(this.result.amount);
                }
            }
        }
        return stringBuilder.toString();
    }

}