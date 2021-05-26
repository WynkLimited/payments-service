package in.wynk.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPreferredPaymentsRequest {

    private int planId;
    private String uid;
    private String deviceId;
    private String paymentCode;
    private String paymentGroup;

}
