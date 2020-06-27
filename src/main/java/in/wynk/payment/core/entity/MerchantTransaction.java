package in.wynk.payment.core.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "merchant_transaction")
public class MerchantTransaction {

    @Id
    @Column(name = "merchant_transaction_id")
    private String externalTransactionId;
    @Column(name = "merchant_request", nullable = false)
    private String request;
    @Column(name = "merchant_response", nullable = false)
    private String response;

}
