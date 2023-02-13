package in.wynk.payment.core.dao.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.Column;
import javax.persistence.Table;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "transaction")

public class TransactionDiscountrate {
    @Column(name = "transaction_id")
    private String id;
    @Column(name = "merchantkey")
    private Integer key;

}
