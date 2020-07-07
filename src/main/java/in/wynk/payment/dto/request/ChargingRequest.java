package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.Getter;


@Getter
public class ChargingRequest {

    private int planId;
    private PaymentCode paymentCode;
    private String couponId;
}
