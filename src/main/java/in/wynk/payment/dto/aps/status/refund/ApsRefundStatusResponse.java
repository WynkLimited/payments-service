package in.wynk.payment.dto.aps.status.refund;

import lombok.Getter;

@Getter
public class ApsRefundStatusResponse {
    private String refundId;
    private String refundSystemId;
    private String refundAmount;
    private String refundStatus;
}
