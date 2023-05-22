package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@Getter
@ToString
@NoArgsConstructor
public class RefundInfo implements Serializable {
    private String refundId;
    private String refundSystemId;
    private double refundAmount;
    private String refundStatus;
    private boolean history;
    private String arn;
    private long refundDate;
}
