package in.wynk.payment.dto.aps.request.refund;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ApsExternalPaymentRefundRequest {
    private String pgId;
    private String postingId;
    private String refundAmount;
}
