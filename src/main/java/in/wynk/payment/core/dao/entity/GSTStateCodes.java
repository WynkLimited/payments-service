package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@SuperBuilder
@Document("gst_state_codes")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class GSTStateCodes extends MongoBaseEntity<String> {
    @Field("iso_state_code")
    private String stateCode;
    @Field("state_name")
    private String stateName;
    @Field("country_code")
    private String countryCode;
    @Field("country_name")
    private String countryName;
}
