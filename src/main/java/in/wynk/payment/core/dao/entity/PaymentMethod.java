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
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentMethod extends MongoBaseEntity {
    private PaymentGroup group;
    private Map<String, Object> meta;
    private String displayName;
    private PaymentCode paymentCode;
    private int hierarchy;
}
