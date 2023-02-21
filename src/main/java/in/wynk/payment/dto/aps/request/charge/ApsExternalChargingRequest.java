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
public class ApsExternalChargingRequest<T extends AbstractPaymentInfo> {

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
     * Signature is Order checksum for validation
     */
    private String signature;
    /**
     * Send this flag as false in case of bill payment otherwise false for other/dummy transactions.
     */
    @Builder.Default
    @JsonProperty("isPennyDropTxn")
    private boolean pennyDropTxn = false;
}
