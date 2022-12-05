package in.wynk.payment.dto.payu.external.charge.upi.intent;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.service.PaymentCachingService;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.PaymentErrorType.PAY104;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@NoArgsConstructor
public class PayUUpiIntentExternalChargingResponse {

    private Result result;

    @Getter
    @NoArgsConstructor
    public class Result {

        private String amount;
        private String paymentId;
        private String merchantVpa;
        private String acsTemplate;
        private String merchantName;
        private String intentURIData;

    }

    public String getDeepLink() {
        if (Objects.nonNull(this.result) && Objects.nonNull(this.result.intentURIData)) {
            PaymentCachingService paymentCachingService = BeanLocatorFactory.getBean(PaymentCachingService.class);
            String offerTitle = paymentCachingService.getOffer(paymentCachingService.getPlan(TransactionContext.get().getPlanId()).getLinkedOfferId()).getTitle();
            Map<String, String> map = Arrays.stream(this.result.intentURIData.split("&")).map(s -> s.split("=", 2)).filter(p-> StringUtils.isNotBlank(p[1])).collect(Collectors.toMap(x-> x[0], x -> x[1]));
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append("upi://pay");
            stringBuilder.append("?pa=").append(map.getOrDefault("pa", this.result.merchantVpa));
            stringBuilder.append("&pn=").append(map.getOrDefault("pn", "Wynk Limited"));
            stringBuilder.append("&tr=").append(map.getOrDefault("tr", this.result.paymentId));
            stringBuilder.append("&am=").append(map.getOrDefault("am", this.result.amount));
            stringBuilder.append("&cu=").append(map.getOrDefault("cu", "INR"));
            stringBuilder.append("&tn=").append(StringUtils.isNotBlank(offerTitle) ? offerTitle : map.get("tn"));
            // stringBuilder.append("&mc=").append(PAYU_MERCHANT_CODE);
            // stringBuilder.append("&tid=").append(TransactionContext.get().getIdStr());
            return stringBuilder.toString();
        }
        throw new WynkRuntimeException(PAY104);
    }

}