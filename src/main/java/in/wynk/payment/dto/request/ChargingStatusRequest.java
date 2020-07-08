package in.wynk.payment.dto.request;

import in.wynk.commons.dto.PlanPeriodDTO;
import in.wynk.commons.enums.FetchStrategy;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.payment.enums.StatusMode;
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


    private StatusMode mode;
}
