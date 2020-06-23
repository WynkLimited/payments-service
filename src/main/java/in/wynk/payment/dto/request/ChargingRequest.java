package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentOption;
import lombok.*;
import lombok.Builder;

@Builder
@Data
public class ChargingRequest {
    private String sessionId;
    private String partnerProductId;
    private String couponId;
    private PaymentOption paymentOption;
}
