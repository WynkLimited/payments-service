package in.wynk.payment.core.dao.entity;

import in.wynk.commons.entity.MongoBaseEntity;
import lombok.Getter;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("user_preferred_payments")
@Getter
public class UserPreferredPayment extends MongoBaseEntity {

    private String uid;
    private Payment option;


    public static final class Builder {
        private String id;
        private String uid;
        private Payment option;

        public Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder uid(String uid) {
            this.uid = uid;
            return this;
        }

        public Builder option(Payment option) {
            this.option = option;
            return this;
        }

        public UserPreferredPayment build() {
            UserPreferredPayment userPreferredPayment = new UserPreferredPayment();
            userPreferredPayment.setId(id);
            userPreferredPayment.option = this.option;
            userPreferredPayment.uid = this.uid;
            return userPreferredPayment;
        }
    }
}
