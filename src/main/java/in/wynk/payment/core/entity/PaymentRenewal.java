package in.wynk.payment.core.entity;

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
import java.util.UUID;

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
    private UUID transaction;

    @Column(name = "renewal_day", nullable = false)
    @Temporal(TemporalType.DATE)
    private Calendar day;

    @Column(name = "renewal_hour", nullable = false)
    @Temporal(TemporalType.TIME)
    private Date hour;

}
