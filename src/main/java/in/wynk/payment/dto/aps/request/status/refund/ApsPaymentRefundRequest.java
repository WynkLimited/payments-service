package in.wynk.payment.dto.aps.request.status.refund;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApsPaymentRefundRequest {
    private String pgId;
    private String postingId;
    private String refundAmount;
}
