package in.wynk.payment.dto.aps.response.status.refund;

import in.wynk.payment.dto.aps.common.RefundInfo;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class ApsRefundStatusResponse extends RefundInfo {
    private String pgId;
    private String pgStatus;
    private String rrn;
}
