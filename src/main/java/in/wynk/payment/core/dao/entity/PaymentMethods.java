package in.wynk.payment.core.dao.entity;

import in.wynk.commons.enums.PaymentGroup;
import in.wynk.commons.enums.State;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Map;

@Document("payment_methods")
public class PaymentMethods {

    @Id
    private String id;
    private PaymentGroup group;
    private Map<String, Object> meta;
    private String displayName;
    private String paymentCode;
    private State state;
    private int hierarchy;

}
