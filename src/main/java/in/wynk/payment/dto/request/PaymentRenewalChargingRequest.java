package in.wynk.payment.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymentRenewalChargingRequest {

    private String id;
    private String uid;
    private String msisdn;
    private Integer planId;
    private int attemptSequence;
    private String clientAlias;

}
