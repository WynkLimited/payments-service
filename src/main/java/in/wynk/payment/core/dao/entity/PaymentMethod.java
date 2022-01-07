package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.List;
import java.util.Map;

@Getter
@SuperBuilder
@Document("payment_methods")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentMethod extends MongoBaseEntity<String> {

    private int hierarchy;

    private boolean trialSupported;
    private boolean autoRenewSupported;

    private String group;
    private String iconUrl;
    private String subtitle;
    private String displayName;
    private String paymentCode;
    private String ruleExpression;

    private List<String> suffixes;
    private Map<String, Object> meta;

    public PaymentCode getPaymentCode() {
        return PaymentCodeCachingService.getFromPaymentCode(this.paymentCode);
    }

}