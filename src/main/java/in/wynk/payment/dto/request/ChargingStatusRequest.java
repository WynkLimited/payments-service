package in.wynk.payment.dto.request;

import in.wynk.payment.enums.paytm.StatusMode;
import lombok.*;

@Getter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class ChargingStatusRequest {
    private String sessionId;
    private String transactionId;
    private StatusMode statusMode;
}
