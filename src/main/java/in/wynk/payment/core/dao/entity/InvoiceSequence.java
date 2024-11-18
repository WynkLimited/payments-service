package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

@Getter
@SuperBuilder
@Document("invoice_sequence")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InvoiceSequence extends MongoBaseEntity<String> {
    @Setter
    @Field("sequence")
    private long sequenceNumber;
    private String identifier;
    private CreditNote creditNote;
    @Getter
    @Setter
    public static class CreditNote {
        @Field("cnsequence")
        private long cnSequence;
        @Field("cnidentifier")
        private String cnIdentifier;
    }
}
