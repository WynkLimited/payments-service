package in.wynk.payment.core.dto;

import lombok.*;

import javax.persistence.*;
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
