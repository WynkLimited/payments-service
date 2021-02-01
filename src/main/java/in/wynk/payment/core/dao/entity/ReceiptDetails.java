package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

@Getter
@ToString
@SuperBuilder
@Document(collection = "ReceiptDetails")
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class ReceiptDetails extends MongoBaseEntity implements Serializable {

    private String msisdn;
    private String uid;
    private int planId;
    @Builder.Default
    private long expiry = -1;

    public String getUserId() {
        return super.getId();
    }

}
