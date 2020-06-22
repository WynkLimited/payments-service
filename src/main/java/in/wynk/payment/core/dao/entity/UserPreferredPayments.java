package in.wynk.payment.core.dao.entity;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("user_preferred_payments")
@Data
public class UserPreferredPayments {

    @Id
    private String id;
    private String uid;
    private PaymentOption option;

}
