package in.wynk.payment.dto.aps.response.order;

import in.wynk.payment.constant.OrderStatus;
import in.wynk.payment.dto.aps.common.Currency;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@ToString
@NoArgsConstructor
public class OrderInfo  extends in.wynk.payment.dto.aps.common.OrderInfo {
    private double totalPostingAmount;
    private double totalDiscountAmount;
    private OrderStatus orderStatus;
    private Currency requester;
    private long createdAt;
    private long updatedAt;
    private Number itemsInOpenState;
}
