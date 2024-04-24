package in.wynk.payment.core.dao.entity;

import in.wynk.audit.Auditable;
import in.wynk.data.entity.MongoBaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.io.Serializable;

@Getter
@Setter
@ToString
@SuperBuilder
@Document(collection = "receipt_details")
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public abstract class ReceiptDetails extends MongoBaseEntity<String> implements Auditable, Serializable {

    private String msisdn;
    @Setter
    private String uid;
    @Field("plan_id")
    private int planId;
    @Field("item_id")
    private String itemId;
    @Builder.Default
    private long expiry = -1;
    @Field("payment_transaction_id")
    private String paymentTransactionId;
    @Field("receipt_transaction_id")
    private String receiptTransactionId;

    public abstract Integer getNotificationType();
    public abstract boolean isRenew ();
}
