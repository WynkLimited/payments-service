package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import in.wynk.payment.core.constant.PaymentCode;
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
public class PaymentMethod extends MongoBaseEntity {
    private String group;
    private Map<String, Object> meta;
    private String displayName;
    private String subtitle;
    private String iconUrl;
    private PaymentCode paymentCode;
    private int hierarchy;
    private boolean autoRenewSupported;
    private List<String> suffixes;
}
