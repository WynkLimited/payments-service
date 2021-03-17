package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Getter
public class WynkUserExtUserMapping extends MongoBaseEntity {

    @Setter
    private String externalUserId;
    private final String msisdn;
}
