package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Getter
@SuperBuilder
@Document("payment_groups")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class PaymentGroup extends MongoBaseEntity<String> {
    private String displayName;
    private int hierarchy;
    private String description;
    private Map<String, Object> meta;

    @Override
    public int hashCode() {
        return getId().hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        return  getId().equals(obj);
    }
}
