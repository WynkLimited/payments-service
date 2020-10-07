package in.wynk.payment.core.dao.entity;

import in.wynk.common.enums.TransactionEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Index;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;
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
public class PaymentRenewal implements Serializable {

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

    public TransactionEvent getTransactionEvent() {
        return TransactionEvent.valueOf(transactionEvent);
    }

}
