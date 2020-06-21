package in.wynk.payment.dto.request;

import in.wynk.payment.enums.ItunesReceiptType;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingStatusRequest {

    private String sessionId;
    private String transactionId;
    private String uid;
    private String receipt;

}
