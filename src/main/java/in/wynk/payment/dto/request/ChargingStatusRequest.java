package in.wynk.payment.dto.request;

import in.wynk.payment.core.enums.StatusMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class ChargingStatusRequest {

    private String transactionId;
    private StatusMode mode;
}
