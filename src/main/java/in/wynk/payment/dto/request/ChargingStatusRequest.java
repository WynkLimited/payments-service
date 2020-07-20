package in.wynk.payment.dto.request;

import in.wynk.commons.enums.TransactionEvent;
import in.wynk.payment.enums.StatusMode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Date;

@Getter
@Builder
@RequiredArgsConstructor
@AllArgsConstructor
public class ChargingStatusRequest {

    private String transactionId;
    private StatusMode mode;
}
