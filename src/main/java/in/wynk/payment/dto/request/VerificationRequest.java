package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.payu.VerificationType;
import lombok.*;

import javax.validation.constraints.NotNull;

@Getter
@Builder
@ToString
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {

    @NotNull
    @Analysed
    private String verifyValue;

    @NotNull
    private PaymentCode paymentCode;

    @NotNull
    @Analysed
    private VerificationType verificationType;

}