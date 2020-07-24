package in.wynk.payment.core.dao.entity;

import com.vladmihalcea.hibernate.type.json.JsonStringType;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "merchant_transaction")
@TypeDef(name = "json", typeClass = JsonStringType.class)
public class MerchantTransaction {

    @Id
    @Column(name = "transaction_id")
    @Setter(AccessLevel.NONE)
    private String id;
    @Column(name = "merchant_transaction_reference_id")
    private String externalTransactionId;
    @Type(type = "json")
    @Column(name = "merchant_request", nullable = false, columnDefinition = "json")
    private Object request;
    @Type(type = "json")
    @Column(name = "merchant_response", nullable = false, columnDefinition = "json")
    private Object response;

    public <T> T getRequest() {
        return (T) request;
    }

    public <T> T getResponse() {
        return (T) response;
    }

}
