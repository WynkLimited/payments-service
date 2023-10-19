package in.wynk.payment.dto.aps.response.order;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.aps.common.ChannelInfo;
import in.wynk.payment.dto.aps.common.PollingConfig;
import in.wynk.payment.dto.aps.common.UserInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
public class ApsOrderStatusResponse {
    private String orderId;
    private OrderInfo orderInfo;
    private OrderPaymentDetails[] paymentDetails;
    private UserInfo userInfo;
    private ChannelInfo channelInfo;
    private FulfilmentInfo[] fulfilmentInfo;
    private PollingConfig pollingConfig;
}
