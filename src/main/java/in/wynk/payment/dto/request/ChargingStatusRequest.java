package in.wynk.payment.dto.request;

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

    private String sessionId;
    private String transactionId;
    private String orderId;
    private Date createdTimeStamp;
    private TransactionEvent transactionEvent;
}
