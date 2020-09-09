package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.payu.VerificationType;
import lombok.*;

@ToString
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@AnalysedEntity
public class VerificationRequest {
    @Analysed
    private VerificationType verificationType;
    @Analysed
    private String verifyValue;
    private PaymentCode paymentCode;
}
