package in.wynk.payment.core.dao.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.audit.Auditable;
import in.wynk.data.entity.MongoBaseEntity;
import lombok.*;
import lombok.experimental.SuperBuilder;
import org.apache.commons.collections4.MapUtils;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;
import java.util.Map;
import java.util.Optional;

@Getter
@Setter
@ToString
@SuperBuilder
@Document(collection = "payment_renewal_details")
@NoArgsConstructor(access = AccessLevel.PACKAGE)
public class PaymentRenewalDetails extends MongoBaseEntity<String> implements Auditable, Serializable {

    @JsonProperty("allowed_attempt_sequence")
    private Integer allowedAttemptSequence;
    private Map<String, Object> meta;
    public <T> Optional<T> get(String key) {
        if (MapUtils.isNotEmpty(meta) && meta.containsKey(key)) return Optional.of((T) meta.get(key));
        return Optional.empty();
    }
}
