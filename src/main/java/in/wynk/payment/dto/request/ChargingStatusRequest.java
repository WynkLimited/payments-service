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
    private Date chargingTimestamp;
    private TransactionEvent transactionEvent;
    private String uid;
    private int planId;
    private StatusMode mode;
}
