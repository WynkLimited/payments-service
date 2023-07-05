package in.wynk.payment.dto.aps.request.status.refund;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Builder
@ToString
public class RefundStatusRequest implements Serializable {
    /**
    * Refund Id for which refund status details needs to be fetched.
    */
    private String refundId;
}
