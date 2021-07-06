package in.wynk.payment.dto.response.payu;

import in.wynk.exception.WynkRuntimeException;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang.StringUtils;

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

    public String getDeepLink(String planTitle) {
        StringBuilder stringBuilder = new StringBuilder();
        if (Objects.nonNull(this.result)) {
            stringBuilder.append("upi://pay?pa=");
            if (StringUtils.isNotBlank(this.result.merchantVpa)) {
                stringBuilder.append(this.result.merchantVpa);
            }
            stringBuilder.append("&pn=Wynk Limited&tr=");
            if (StringUtils.isNotBlank(this.result.paymentId)) {
                stringBuilder.append(this.result.paymentId);
            }
            stringBuilder.append("&am=");
            if (StringUtils.isNotBlank(this.result.amount)) {
                stringBuilder.append(this.result.amount);
            }
            stringBuilder.append("&cu=").append(this.result.intentURIData.split("&cu=")[1].split("&")[0]).append("&tn=").append(planTitle);
        }
        throw new WynkRuntimeException(PAY104);
    }

}