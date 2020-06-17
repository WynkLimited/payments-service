package in.wynk.payment.dto.request;

import in.wynk.payment.constant.PaymentOption;
import lombok.Builder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@Builder
@RequiredArgsConstructor
public class ChargingRequest {

    private final String sessionId;
    private final String partnerProductId;
    private final String couponId;
    private final PaymentOption paymentOption;

}
