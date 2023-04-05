package in.wynk.payment.dto.aps.request.status.refund;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

@Getter
@Builder
@ToString
public class RefundStatusRequest {
    /**
    * Refund Id for which refund status details needs to be fetched.
    */
    private String refundId;
}
