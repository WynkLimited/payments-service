package in.wynk.payment.dto.request;

import lombok.*;

@Getter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class ChargingStatusRequest {
    private String sessionId;
    private String transactionId;
}
