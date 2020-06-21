package in.wynk.payment.dto.request;

import in.wynk.payment.constant.PaymentOption;
import lombok.*;

@Builder
@Data
public class ChargingRequest {
    private String sessionId;
    private String partnerProductId;
    private String couponId;
    private PaymentOption paymentOption;
}
