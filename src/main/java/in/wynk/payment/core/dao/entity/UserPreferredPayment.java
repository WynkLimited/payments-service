package in.wynk.payment.core.dao.entity;
import in.wynk.data.entity.MongoBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;


@Getter
@SuperBuilder
@Document("user_preferred_payments")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class UserPreferredPayment extends MongoBaseEntity {

    private String uid;
    @Setter
    private Payment option;

}
