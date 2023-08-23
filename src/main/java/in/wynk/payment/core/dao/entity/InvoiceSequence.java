package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

@Getter
@SuperBuilder
@Document("invoice_sequence")
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class InvoiceSequence extends MongoBaseEntity<String> {
    private String sequence;
}
