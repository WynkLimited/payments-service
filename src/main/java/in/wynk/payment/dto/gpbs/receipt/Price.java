package in.wynk.payment.dto.gpbs.receipt;

import lombok.Getter;
import lombok.Setter;

/**
 * @author Nishesh Pandey
 */
@Getter
@Setter
public class Price {
    private String priceMicros;
    private String currency;
}
