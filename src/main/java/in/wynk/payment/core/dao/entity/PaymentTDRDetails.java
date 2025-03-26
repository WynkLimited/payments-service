package in.wynk.payment.core.dao.entity;

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
import java.util.Calendar;
import java.util.Date;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "tdr_details", indexes = {@Index(name = "tdr_day_time_index", columnList = "execution_day, execution_min,processed")})
public class PaymentTDRDetails {

    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "transaction_id")
    private Double tdr;

    @Column(name = "plan_id")
    private Integer planId;

    @Column(name = "uid")
    private String uid;

    @Column(name = "reference_id")
    private String reference_id;

    @Column(name = "processed")
    private Boolean processed;

    @Column(name = "execution_time", nullable = false)
    @Temporal(TemporalType.TIMESTAMP)
    private Date executionTime;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "created_timestamp")
    private Calendar createdTimestamp;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "updated_timestamp")
    private Calendar updatedTimestamp;
}
