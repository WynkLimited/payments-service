package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import org.springframework.data.mongodb.core.mapping.Document;

import java.io.Serializable;

@Getter
@SuperBuilder
@NoArgsConstructor
@Document("user_preferred_payments")
public abstract class UserPreferredPayment extends MongoBaseEntity<Key> implements Serializable {}