package in.wynk.payment.dto.request;

import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Builder
@RequiredArgsConstructor
public class ChargingStatusRequest {

    private final String sessionId;
    private final String transactionId;

}
