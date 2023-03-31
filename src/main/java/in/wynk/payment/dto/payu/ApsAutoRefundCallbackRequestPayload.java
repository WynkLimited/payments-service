package in.wynk.payment.dto.payu;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */

@Getter
@SuperBuilder
@NoArgsConstructor
public class ApsAutoRefundCallbackRequestPayload extends ApsCallBackRequestPayload {
    private String refundId;
    private String refundSystemId;
    private String refundOrderId;
    private String bankRefId;
}
