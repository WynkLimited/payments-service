package in.wynk.payment.dto.aps.request.order;

import in.wynk.payment.dto.aps.common.ChannelInfo;
import in.wynk.payment.dto.aps.common.OrderInfo;
import in.wynk.payment.dto.aps.common.UserInfo;
import lombok.Getter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import java.util.List;

/**
 * @author Nishesh Pandey
 */

@Getter
@SuperBuilder
@ToString
public class ApsOrderRequest {
    private OrderInfo orderInfo;
    private List<OrderItem> items;
    private UserInfo userInfo;
    private ChannelInfo channelInfo;
}
