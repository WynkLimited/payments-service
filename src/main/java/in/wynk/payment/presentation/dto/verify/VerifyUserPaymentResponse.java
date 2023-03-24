package in.wynk.payment.presentation.dto.verify;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.payu.VerificationType;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public abstract class VerifyUserPaymentResponse {
    private final VerificationType verificationType;
    private final boolean valid;
}
