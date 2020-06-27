package in.wynk.payment.core.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
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
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(name = "payment_error_id")
    private Long id;
    @Column(name = "error_code", nullable = false)
    private String code;
    @Column(name = "error_description", nullable = false)
    private String description;
}
