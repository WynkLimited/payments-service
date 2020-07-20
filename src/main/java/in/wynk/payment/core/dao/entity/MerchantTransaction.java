package in.wynk.payment.core.dao.entity;

import com.vladmihalcea.hibernate.type.json.JsonStringType;
import lombok.*;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "merchant_transaction")
@TypeDef(name = "json", typeClass = JsonStringType.class)
public class MerchantTransaction {

    @Id
    @Column(name = "merchant_transaction_id")
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    @Setter
    @Column(name = "merchant_transaction_reference_id")
    private String externalTransactionId;
    @Setter
    @Type(type = "json")
    @Column(name = "merchant_request", nullable = false, columnDefinition = "json")
    private Object request;
    @Setter
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
