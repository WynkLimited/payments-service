package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

@Getter
@ToString
@SuperBuilder
@Document(collection = "SkuMapping")
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class SkuMapping extends MongoBaseEntity<String> implements Serializable {
    private String oldSku;
    private String newSku;
}
