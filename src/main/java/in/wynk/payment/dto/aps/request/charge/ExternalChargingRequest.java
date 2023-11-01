package in.wynk.payment.dto.aps.request.charge;

import com.fasterxml.jackson.annotation.JsonInclude;
import in.wynk.payment.dto.aps.common.AbstractPaymentInfo;
import in.wynk.payment.dto.aps.common.AbstractUserInfo;
import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalChargingRequest<T extends AbstractPaymentInfo> {

    /**
     * Partner Correlation ID. i.e self
     */
    private String orderId;

    /**
     * Payment Info
     */
    private T paymentInfo;
    /**
     * User Info
     */
    private AbstractUserInfo userInfo;

    /**
     * Redirection info
     */
    private ChannelInfo channelInfo;
}
