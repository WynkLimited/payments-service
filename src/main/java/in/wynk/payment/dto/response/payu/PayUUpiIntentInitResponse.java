package in.wynk.payment.dto.response.payu;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.service.PaymentCachingService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY104;
import static in.wynk.payment.dto.payu.PayUConstants.PAYU_MERCHANT_CODE;

@Getter
@NoArgsConstructor
public class PayUUpiIntentInitResponse extends AbstractPayUUpiResponse<PayUUpiIntentInitResponse.IntentResult> {

    @Getter
    @NoArgsConstructor
    public final class IntentResult extends AbstractPayUUpiResponse.Result {
        private String amount;
        private String paymentId;
        private String merchantVpa;
        private String merchantName;
        private String intentURIData;

    }

    public String getDeepLink(boolean autoRenew) {
        if (Objects.nonNull(this.getResult()) && Objects.nonNull(this.getResult().intentURIData)) {
            PaymentCachingService paymentCachingService = BeanLocatorFactory.getBean(PaymentCachingService.class);
            String offerTitle = paymentCachingService.getOffer(paymentCachingService.getPlan(TransactionContext.get().getPlanId()).getLinkedOfferId()).getTitle();
            Map<String, String> map = Arrays.stream(this.getResult().intentURIData.split("&")).map(s -> s.split("=", 2)).filter(p -> StringUtils.isNotBlank(p[1])).collect(Collectors.toMap(x -> x[0], x -> x[1]));
            StringBuilder stringBuilder = new StringBuilder();
            if (!autoRenew) stringBuilder.append("upi://pay?");
            else {
                stringBuilder.append("upi://mandate?");
                if (map.containsKey("mn")) stringBuilder.append("&mn=").append(map.get("mn"));
                if (map.containsKey("rev")) stringBuilder.append("&rev=").append(map.get("rev"));
                if (map.containsKey("mode")) stringBuilder.append("&mode=").append(map.get("mode"));
                if (map.containsKey("recur")) stringBuilder.append("&recur=").append(map.get("recur"));
                if (map.containsKey("orgid")) stringBuilder.append("&orgid=").append(map.get("orgid"));
                if (map.containsKey("block")) stringBuilder.append("&block=").append(map.get("block"));
                if (map.containsKey("amrule")) stringBuilder.append("&amrule=").append(map.get("amrule"));
                if (map.containsKey("purpose")) stringBuilder.append("&purpose=").append(map.get("purpose"));
                if (map.containsKey("txnType")) stringBuilder.append("&txnType=").append(map.get("txnType"));
                if (map.containsKey("recurtype")) stringBuilder.append("&recurtype=").append(map.get("recurtype"));
                if (map.containsKey("recurvalue")) stringBuilder.append("&recurvalue=").append(map.get("recurvalue"));
                if (map.containsKey("validityend")) stringBuilder.append("&validityend=").append(map.get("validityend"));
                if (map.containsKey("validitystart")) stringBuilder.append("&validitystart=").append(map.get("validitystart"));
                stringBuilder.append("&");
            }
            stringBuilder.append("pa=").append(map.getOrDefault("pa", this.getResult().merchantVpa));
            stringBuilder.append("&pn=").append(map.getOrDefault("pn", "Wynk Limited"));
            stringBuilder.append("&tr=").append(map.getOrDefault("tr", this.getResult().paymentId));
            stringBuilder.append("&am=").append(map.getOrDefault("am", this.getResult().amount));
            stringBuilder.append("&cu=").append(map.getOrDefault("cu", "INR"));
            stringBuilder.append("&tn=").append(StringUtils.isNotBlank(offerTitle) ? offerTitle : map.get("tn"));
            stringBuilder.append("&mc=").append(PAYU_MERCHANT_CODE);
            stringBuilder.append("&tid=").append(TransactionContext.get().getIdStr().replaceAll("-", ""));
            return stringBuilder.toString();
        }
        throw new WynkRuntimeException(PAY104);
    }

}