package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.enums.PaymentGroup;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Getter
@SuperBuilder
@Document("payment_methods")
@NoArgsConstructor(access = AccessLevel.NONE)
public class PaymentMethod extends MongoBaseEntity {
    private final PaymentGroup group;
    private final Map<String, Object> meta;
    private final String displayName;
    private final PaymentCode paymentCode;
    private final int hierarchy;
}
