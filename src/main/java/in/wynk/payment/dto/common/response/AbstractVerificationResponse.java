package in.wynk.payment.dto.common.response;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.payu.VerificationType;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
public abstract class AbstractVerificationResponse {
    private String verifyValue;
    private VerificationType verificationType;
    private boolean valid;
    private boolean autoRenewSupported;
}
