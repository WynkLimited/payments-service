package in.wynk.payment.dto.aps.common;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * @author Nishesh Pandey
 */
@Getter
@Builder
@ToString
public class OrderInfo {
    private double orderAmount;
    private Currency currency;
}
