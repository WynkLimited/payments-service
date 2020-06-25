package in.wynk.payment.dto.request;

import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingStatusRequest {

    private String sessionId;
    private String transactionId;
    private String uid;
    private String receipt;
    private int productId;

}
