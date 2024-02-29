package in.wynk.payment.core.dao.entity;

import in.wynk.audit.entity.AuditableEntity;
import in.wynk.common.enums.PaymentEvent;
import lombok.*;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Calendar;
import java.util.Date;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payment_renewal", indexes = {@Index(name = "payment_renewal_day_time_index", columnList = "renewal_day, renewal_hour")})
public class PaymentRenewal extends AuditableEntity implements Serializable {

    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "renewal_day", nullable = false)
    @Temporal(TemporalType.DATE)
    private Calendar day;

    @Column(name = "renewal_hour", nullable = false)
    @Temporal(TemporalType.TIME)
    private Date hour;

    @Column(name = "merchant_transaction_event")
    private String transactionEvent;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_timestamp")
    private Calendar createdTimestamp;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_timestamp")
    private Calendar updatedTimestamp;

    @Column(name = "attempt_sequence")
    private Integer attemptSequence;

    @Column(name = "initial_transaction_id")
    private String initialTransactionId;

    @Column(name = "last_success_transaction_id")
    private String lastSuccessTransactionId;

    public PaymentEvent getTransactionEvent() {
        return PaymentEvent.valueOf(transactionEvent);
    }

}
