package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class RefundInfo {
    private String refundId;
    private String refundSystemId;
    private double refundAmount;
    private String refundStatus;
}
