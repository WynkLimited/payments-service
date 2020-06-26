package in.wynk.payment.dto;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class VerificationRequest {

    private String uid;
    private String receipt;
    private int planId;
    private PaymentCode paymentCode;

}
