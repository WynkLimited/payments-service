package in.wynk.payment.core.dao.entity;

import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.*;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transaction")
public class Transaction {

    @Id
    @GenericGenerator(name = "transaction_seq_id", strategy = "in.wynk.payment.core.utils.TransactionIdentityGenerator")
    @GeneratedValue(generator = "transaction_seq_id")
    @Setter(AccessLevel.NONE)
    @Column(name = "transaction_id")
    private String id;

    @Column(name = "plan_id")
    private Integer planId;

    @Column(name = "paid_amount")
    private double amount;

    @Column(name = "discount_amount")
    private double discount;

    @Column(name = "init_timestamp")
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar initTime;

    @Column(name = "uid")
    private String uid;

    @Column(name = "msisdn")
    private String msisdn;

    @Column(name = "client_id")
    private String clientId;

    @Column(name = "item_id")
    private String itemId;

    @Column(name = "payment_channel")
    private String paymentChannel;

    @Column(name = "transaction_type")
    private String type;

    @Column(name = "transaction_status")
    private String status;

    @Column(name = "coupon_id")
    private String coupon;

    @Column(name = "exit_timestamp")
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar exitTime;

    @Column(name = "user_consent_timestamp")
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar consent;

    private transient Map<String, Object> paymentMetaData;

    public TransactionEvent getType() {
        return TransactionEvent.valueOf(type);
    }

    public TransactionStatus getStatus() {
        return TransactionStatus.valueOf(status);
    }

    public UUID getId() {
        return id != null ? UUID.fromString(id): null;
    }

    public String getIdStr(){
        return id;
    }

    public PaymentCode getPaymentChannel() {
        return PaymentCode.valueOf(paymentChannel);
    }

    public <R> R getValueFromPaymentMetaData(String key) {
        return (R) paymentMetaData.get(key);
    }

    public <R> void putValueInPaymentMetaData(String key, R value) {
        if(Objects.isNull(paymentMetaData)){
            paymentMetaData = new HashMap<>();
        }
        paymentMetaData.put(key, value);
    }

}
