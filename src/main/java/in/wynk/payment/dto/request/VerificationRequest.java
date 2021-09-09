package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.session.context.SessionContextHolder;
import lombok.*;

import static in.wynk.common.constant.BaseConstants.CLIENT;

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

    public String getClient() {
        return SessionContextHolder.<SessionDTO>getBody().get(CLIENT);
    }
}
