package in.wynk.payment.dto.gpbs.response.receipt;

import in.wynk.payment.dto.gpbs.Price;
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
