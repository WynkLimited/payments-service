package in.wynk.payment.core.dto;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.revenue.commons.TransactionEvent;
import in.wynk.revenue.commons.TransactionStatus;
import lombok.*;
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

    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "paid_amount")
    private float amount;

    @Column(name = "discount_amount")
    private float discount;

    @Column(name = "init_timestamp")
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar initTime;

    @Column(name = "uid")
    private String uid;

    @Column(name = "msisdn")
    private String msisdn;

    @Column(name = "item_id")
    private String itemId;

    @Column(name = "payment_channel")
    private PaymentCode paymentChannel;

    @Column(name = "service")
    private String service;

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

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "fk_error_id", referencedColumnName = "payment_error_id")
    private PaymentError paymentError;

    @OneToOne(fetch = FetchType.LAZY, cascade = CascadeType.ALL)
    @JoinColumn(name = "fk_merchant_id", referencedColumnName = "merchant_transaction_id")
    private MerchantTransaction merchantTransaction;

    public TransactionEvent getType() {
        return TransactionEvent.valueOf(type);
    }

    public TransactionStatus getStatus() {
        return TransactionStatus.valueOf(status);
    }

    public UUID getId() {
        return id != null ? UUID.fromString(id): null;
    }

}
