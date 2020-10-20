package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

@Document(collection = "ReceiptDetails")
@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public abstract class ReceiptDetails extends MongoBaseEntity implements Serializable {

    private String msisdn;
    private String uid;
    private int planId;

    public String getUserId() {
        return super.getId();
    }

}
