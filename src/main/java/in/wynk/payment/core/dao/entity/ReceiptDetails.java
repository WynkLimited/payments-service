package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;

@Getter
@ToString
@Setter
@SuperBuilder
@Document(collection = "receipt_details")
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class ReceiptDetails extends MongoBaseEntity<String> implements Serializable {

    private String msisdn;
    private String uid;
    @Field("plan_id")
    private int planId;
    @Builder.Default
    private long expiry = -1;
    @Field("payment_transaction_id")
    private String paymentTransactionId;
    @Field("receipt_transaction_id")
    private String receiptTransactionId;

}
