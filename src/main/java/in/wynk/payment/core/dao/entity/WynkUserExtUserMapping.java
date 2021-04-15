package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

@SuperBuilder
@Getter
@Document("wynk_user_ext_user_mapping")
public class WynkUserExtUserMapping extends MongoBaseEntity {

    @Setter
    private String externalUserId;
    private final String msisdn;
}
