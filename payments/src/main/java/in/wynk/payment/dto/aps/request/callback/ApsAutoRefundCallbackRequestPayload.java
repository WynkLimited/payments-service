package in.wynk.payment.dto.aps.request.callback;

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
    private boolean autoRefund;
    private Long refundDate;
}
