package in.wynk.payment.dto.request;

import in.wynk.payment.constant.PaymentOption;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingRequest {

    private String sessionId;
    private String partnerProductId;
    private String couponId;
    private PaymentOption paymentOption;

}
