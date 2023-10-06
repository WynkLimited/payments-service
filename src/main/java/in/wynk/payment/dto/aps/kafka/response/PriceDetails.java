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
public class PriceDetails {
    private String currency;
    private long price;
    private long discountPrice;
}
