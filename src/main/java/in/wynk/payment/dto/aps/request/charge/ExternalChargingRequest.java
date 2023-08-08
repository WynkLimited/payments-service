package in.wynk.payment.dto.aps.request.charge;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import in.wynk.payment.dto.aps.common.AbstractPaymentInfo;
import in.wynk.payment.dto.aps.common.ChannelInfo;
import in.wynk.payment.dto.aps.common.UserInfo;
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
    private UserInfo userInfo;

    /**
     * Redirection info
     */
    private ChannelInfo channelInfo;

    /**
     * Send this flag as true in case of bill payment(AUTO Pay case) otherwise false for penny drop and paymentAmount will be refunded in case of penny drop.
     */
    @Builder.Default
    @JsonProperty("isBillPayment")
    private boolean billPayment = true;
}
