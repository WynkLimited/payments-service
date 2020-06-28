package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.ToString;

@Builder
@Getter
@RequiredArgsConstructor
@AllArgsConstructor
@ToString
public class ChargingRequest {

    private String planId;
    private String couponId;
    private PaymentCode paymentCode;
}
