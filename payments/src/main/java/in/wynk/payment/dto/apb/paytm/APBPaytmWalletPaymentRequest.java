package in.wynk.payment.dto.apb.paytm;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Builder
@Getter
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PRIVATE)
public class APBPaytmWalletPaymentRequest {
    private String orderId;
    private APBPaytmPaymentInfo paymentInfo;
    private APBPaytmUserInfo userInfo;
    private APBPaytmChannelInfo channelInfo;
    private String channel;
}
