package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.*;

@Builder
@Getter
@RequiredArgsConstructor
@AllArgsConstructor
public class PaymentRenewalRequest {
    private String sessionId;
    private String partnerProductId;
    private String couponId;
    private PaymentCode paymentOption;
}
