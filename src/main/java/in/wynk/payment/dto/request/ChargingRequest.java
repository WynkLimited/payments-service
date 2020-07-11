package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.Builder;
import lombok.Getter;


@Getter
@Builder
public class ChargingRequest {

    private int planId;
    private PaymentCode paymentCode;
    private String couponId;
}
