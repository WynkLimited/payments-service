package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.scheduler.queue.dto.MongoBaseEntityMessage;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Getter
@SuperBuilder
@Document("payment_methods")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentMethod extends MongoBaseEntityMessage<String> {

    private int hierarchy;

    private boolean trialSupported;
    private boolean saveDetailsSupported;
    private boolean autoRenewSupported;
    private boolean mandateSupported;
    private boolean inAppOtpSupport;
    private boolean otpLessSupport;
    private boolean itemPurchaseSupported;

    private String alias;
    private String group;
    private String flowType;
    private String iconUrl;
    private String subtitle;
    private String displayName;
    private String paymentCode;
    private String ruleExpression;

    private String tag;
    private List<String> suffixes;
    private Map<String, Object> meta;

    public PaymentGateway getPaymentCode() {
        return PaymentCodeCachingService.getFromPaymentCode(this.paymentCode);
    }

    public String getAlias() {
        return StringUtils.isNotEmpty(alias) ? alias: getId();
    }

    public String getTag() {
        return StringUtils.isEmpty(tag) ? getId(): tag;
    }

    public boolean isMandateSupported () {
        return mandateSupported && autoRenewSupported;
    }

    public boolean isTrialSupported () {
        return trialSupported && autoRenewSupported;
    }
}