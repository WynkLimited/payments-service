package in.wynk.payment.core.dao.entity;

import com.vladmihalcea.hibernate.type.json.JsonStringType;
import in.wynk.revenue.commons.TransactionEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Type;
import org.hibernate.annotations.TypeDef;

import javax.persistence.*;

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
