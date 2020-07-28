package in.wynk.payment.dto.request;

import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.payment.core.enums.PaymentCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {
    VerificationType verificationType;
    String verifyValue;
    PaymentCode paymentCode;
}
