package in.wynk.payment.core.dao.entity;

import lombok.*;

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
@Table(name = "payment_error")
public class PaymentError {

    @Id
    @Column(name = "transaction_id")
    @Setter(AccessLevel.NONE)
    private String id;
    @Column(name = "error_code", nullable = false)
    private String code;
    @Column(name = "error_description", nullable = false)
    private String description;
}
