package in.wynk.payment.dto.gpbs.receipt;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class SubscriptionPriceChange {
    private Price price;
    private Integer state;
}
