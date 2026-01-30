package in.wynk.payment.core.dao.entity;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.audit.entity.AuditableEntity;
import in.wynk.common.constant.BaseConstants;
import lombok.*;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Getter
@Setter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "payment_error")
public class PaymentError extends AuditableEntity  {

    @Id
    @Analysed(name = BaseConstants.TRANSACTION_ID)
    @Column(name = "transaction_id")
    @Setter(AccessLevel.NONE)
    private String id;
    @Analysed(name = "error_code")
    @Column(name = "error_code", nullable = false)
    private String code;
    @Analysed(name = "error_description")
    @Column(name = "error_description", nullable = false)
    private String description;
}
