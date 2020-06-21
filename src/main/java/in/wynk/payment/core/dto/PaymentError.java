package in.wynk.payment.core.dto;

import lombok.*;

import javax.persistence.*;

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
