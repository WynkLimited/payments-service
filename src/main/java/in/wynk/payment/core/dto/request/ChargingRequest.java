package in.wynk.payment.core.dto.request;

import in.wynk.payment.core.enums.PaymentCode;
import lombok.*;


@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor
public class ChargingRequest {

    private int planId;
    private PaymentCode paymentCode;
    private String couponId;
}
