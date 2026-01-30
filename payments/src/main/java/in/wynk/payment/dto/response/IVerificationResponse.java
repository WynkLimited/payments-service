package in.wynk.payment.dto.response;

import com.github.annotation.analytic.core.annotations.Analysed;

public interface IVerificationResponse {
    @Analysed(name = "valid")
    boolean isValid();
}