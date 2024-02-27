package in.wynk.payment.core.dao.entity;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import com.vladmihalcea.hibernate.type.json.JsonStringType;
import in.wynk.audit.entity.AuditableEntity;
import in.wynk.common.constant.BaseConstants;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;

@Entity
@Getter
@Setter
@Builder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "merchant_transaction")
@TypeDef(name = "json", typeClass = JsonStringType.class)
@NamedQueries({
        @NamedQuery(name = "MerchantTransaction.findPartnerReferenceById", query = "SELECT m.externalTransactionId FROM MerchantTransaction m WHERE m.id=:txnId", hints = @QueryHint(name = "javax.persistence.query.timeout", value = "300")),
        @NamedQuery(name = "MerchantTransaction.findTransactionIdByOrderId", query = "SELECT m.id FROM MerchantTransaction m WHERE m.orderId=:orderId", hints = @QueryHint(name = "javax.persistence.query.timeout", value = "300"))
})
public class MerchantTransaction extends AuditableEntity {

    @Id
    @Analysed(name = BaseConstants.TRANSACTION_ID)
    @Column(name = "transaction_id")
    @Setter(AccessLevel.NONE)
    private String id;

    @Analysed
    @Column(name = "order_id")
    private String orderId;

    @Analysed
    @Column(name = "external_token_reference_id")
    private String externalTokenReferenceId;

    @Analysed
    @Column(name = "merchant_transaction_reference_id")
    private String externalTransactionId;

    @Type(type = "json")
    @Analysed
    @Column(name = "merchant_request", nullable = false, columnDefinition = "json")
    private Object request;

    @Type(type = "json")
    @Analysed
    @Column(name = "merchant_response", columnDefinition = "json")
    private Object response;

    public <T> T getRequest() {
        return (T) request;
    }

    public <T> T getResponse() {
        return (T) response;
    }

}
