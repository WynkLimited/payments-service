package in.wynk.payment.dto.request;

import in.wynk.commons.dto.PackPeriodDTO;
import in.wynk.commons.enums.FetchStrategy;
import in.wynk.revenue.commons.TransactionEvent;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.Date;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingStatusRequest {

    private String transactionId;
    private Date chargingTimestamp;
    private TransactionEvent transactionEvent;
    private FetchStrategy fetchStrategy;
    private PackPeriodDTO packPeriod;

}
