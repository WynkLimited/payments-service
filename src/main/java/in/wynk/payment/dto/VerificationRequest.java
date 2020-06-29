package in.wynk.payment.dto;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Builder
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {

    private String uid;
    private String receipt;
    private int planId;
    private PaymentCode paymentCode;

}
