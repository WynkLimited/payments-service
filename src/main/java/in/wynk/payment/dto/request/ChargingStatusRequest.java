package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.StatusMode;
import lombok.*;

@Getter
@Builder
@AllArgsConstructor
@RequiredArgsConstructor
public class ChargingStatusRequest {

    @Setter
    private int planId;
    private StatusMode mode;
    private String transactionId;

}
