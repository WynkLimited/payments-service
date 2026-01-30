package in.wynk.payment.core.dao.entity;

import com.github.annotation.analytic.core.annotations.Analysed;
import in.wynk.audit.entity.AuditableEntity;
import in.wynk.common.constant.BaseConstants;
import lombok.*;
import org.springframework.data.domain.Persistable;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Calendar;

@Entity
@Getter
@Builder
@ToString
@AllArgsConstructor
@NoArgsConstructor
@Table(name = "invoice", indexes = {
        @Index(name = "transaction_id_index", columnList = "transaction_id")
})
public class Invoice extends AuditableEntity implements Serializable, Persistable<String> {

    private static final long serialVersionUID = 3241256359460367348L;

    @Id
    @Analysed(name = BaseConstants.INVOICE_ID)
    @Setter(AccessLevel.NONE)
    private String id;

    @Column(name = "transaction_id", updatable = false, nullable = false)
    private String transactionId;

    @Column(name = "invoice_external_id")
    private String invoiceExternalId;

    @Column(name = "amount", nullable = false)
    private double amount;

    @Column(name = "tax_amount", nullable = false)
    private double taxAmount;

    @Column(name = "taxable_value", nullable = false)
    private double taxableValue;

    @Column(name = "cgst")
    private double cgst;

    @Column(name = "sgst")
    private double sgst;

    @Column(name = "igst")
    private double igst;

    @Setter
    @Column(name = "customer_account_no")
    private String customerAccountNumber;

    @Setter
    @Column(name = "status")
    private String status;

    @Setter
    @Column(name = "description")
    private String description;

    @Column(name = "created_timestamp")
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar createdOn;

    @Setter
    @Column(name = "updated_timestamp")
    @Temporal(TemporalType.TIMESTAMP)
    private Calendar updatedOn;

    @Setter
    @Column(name = "retry_count")
    private int retryCount;

    @Builder.Default
    private transient boolean persisted = false;

    @Override
    public boolean isNew() {
        return !persisted;
    }

    public void persisted() {
        this.persisted = true;
    }

}