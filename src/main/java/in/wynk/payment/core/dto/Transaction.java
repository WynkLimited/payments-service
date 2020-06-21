package in.wynk.payment.core.dto;

import lombok.*;
import org.hibernate.annotations.GenericGenerator;

import javax.persistence.*;
import java.util.Calendar;
import java.util.Date;
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
    private UUID id;

    @Column(name = "paid_amount")
    private float amount;

    @Column(name = "init_timestamp")
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar initTime;

    @Column(name = "exit_timestamp")
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar exitTime;

    @Column(name = "item_id")
    private int itemId;

    @Column(name = "user_consent_timestamp")
    private Date consent;

    @Column(name = "uid")
    private String uid;

    @Column(name = "msisdn")
    private String msisdn;

    @Column(name = "payment_channel")
    private String paymentChannel;

    @Column(name = "product_id")
    private Integer productId;

    @Column(name = "service")
    private String service;

    @Column(name = "transaction_type")
    private String type;

    @Column(name = "transaction_status")
    private String status;

    @Column(name = "coupon_id")
    private String coupon;

    @Column(name = "discount_amount")
    private float discount;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_error_id", referencedColumnName = "payment_error_id")
    private PaymentError paymentError;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fk_merchant_id", referencedColumnName = "merchant_transaction_id")
    private MerchantTransaction merchantTransaction;

}
