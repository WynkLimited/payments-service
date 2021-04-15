package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@SuperBuilder
@Document("payment_groups")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentGroup extends MongoBaseEntity {
    private String displayName;
    private int hierarchy;

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return  getId().equals(obj);
    }
}
