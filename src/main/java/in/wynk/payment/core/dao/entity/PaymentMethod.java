package in.wynk.payment.core.dao.entity;

import in.wynk.data.entity.MongoBaseEntity;
import in.wynk.data.enums.State;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentGroup;
import lombok.Getter;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;
@Getter
@Document("payment_methods")
public class PaymentMethod extends MongoBaseEntity {
    
    private PaymentGroup group;
    private Map<String, Object> meta;
    private String displayName;
    private PaymentCode paymentCode;
    private int hierarchy;


    public static final class Builder {
        private String id;
        private PaymentGroup group;
        private Map<String, Object> meta;
        private String displayName;
        private PaymentCode paymentCode;
        private State state;
        private int hierarchy;

        public Builder() {
        }

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder group(PaymentGroup group) {
            this.group = group;
            return this;
        }

        public Builder meta(Map<String, Object> meta) {
            this.meta = meta;
            return this;
        }

        public Builder displayName(String displayName) {
            this.displayName = displayName;
            return this;
        }

        public Builder paymentCode(PaymentCode paymentCode) {
            this.paymentCode = paymentCode;
            return this;
        }

        public Builder state(State state) {
            this.state = state;
            return this;
        }

        public Builder hierarchy(int hierarchy) {
            this.hierarchy = hierarchy;
            return this;
        }

        public PaymentMethod build() {
            PaymentMethod paymentMethod = new PaymentMethod();
            paymentMethod.setId(id);
            paymentMethod.setState(state);
            paymentMethod.setState(state);
            paymentMethod.hierarchy = this.hierarchy;
            paymentMethod.group = this.group;
            paymentMethod.displayName = this.displayName;
            paymentMethod.meta = this.meta;
            paymentMethod.paymentCode = this.paymentCode;
            return paymentMethod;
        }
    }
}
