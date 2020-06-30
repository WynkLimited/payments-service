package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.VerificationType;
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
