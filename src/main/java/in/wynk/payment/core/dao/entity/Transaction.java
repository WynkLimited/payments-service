package in.wynk.payment.core.dao.entity;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.payment.core.constant.PaymentCode;
import lombok.*;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Calendar;
import java.util.UUID;

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

    @Column(name = "client_alias")
    private String clientAlias;

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

    public PaymentEvent getType() {
        return PaymentEvent.valueOf(type);
    }

    public TransactionStatus getStatus() {
        return TransactionStatus.valueOf(status);
    }

    public UUID getId() {
        return id != null ? UUID.fromString(id) : null;
    }

    public String getIdStr() {
        return id;
    }

    public PaymentCode getPaymentChannel() {
        return PaymentCode.valueOf(paymentChannel);
    }

    public String getProductId() {
        if (StringUtils.isEmpty(itemId)) {
            return itemId;
        }
        return String.valueOf(planId);
    }

}
