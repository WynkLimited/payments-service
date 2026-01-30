package in.wynk.payment.dto.aps.response.order;

import in.wynk.payment.constant.ApsS2SOrderStatus;
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
public class ApsS2SOrderInfo extends in.wynk.payment.dto.aps.common.OrderInfo {
    private String requester;
    private long createdAt;
    private long updatedAt;
    private ApsS2SOrderStatus orderStatus;
}
