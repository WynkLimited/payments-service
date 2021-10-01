package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.session.context.SessionContextHolder;
import lombok.*;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Getter
@Builder
@ToString
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public class VerificationRequest {

    @NotBlank
    @Analysed
    private String verifyValue;

    @NotNull
    private PaymentCode paymentCode;

    @NotNull
    @Analysed
    private VerificationType verificationType;

    public String getClient() {
        return SessionContextHolder.<SessionDTO>getBody().get(BaseConstants.CLIENT);
    }


}
