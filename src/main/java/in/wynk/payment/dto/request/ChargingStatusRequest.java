package in.wynk.payment.dto.request;

import in.wynk.payment.enums.Apb.StatusMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingStatusRequest {
    private String sessionId;
    private String transactionId;
    @Builder.Default
    private StatusMode mode = StatusMode.LOCAL;
}
