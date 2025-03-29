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
@Table(name = "tdr_details", indexes = {
        @Index(name = "tdr_query_idx", columnList = "is_processed, execution_time")
})
public class PaymentTDRDetails {

    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    @Column(name = "tdr")
    private Double tdr;

    @Column(name = "plan_id")
    private Integer planId;

    @Column(name = "uid")
    private String uid;

    @Column(name = "reference_id")
    private String referenceId;

    @Column(name = "is_processed", nullable = false)
    private Boolean isProcessed = false;

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
