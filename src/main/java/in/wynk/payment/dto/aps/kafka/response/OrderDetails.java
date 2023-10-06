package in.wynk.payment.dto.aps.kafka.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDetails {
    private String id;
    private String code;
    private int amount;
    private int discount;
    private int mandateAmount;
    private boolean trial;
    private boolean mandate;
    private TaxDetails taxDetails;
}
