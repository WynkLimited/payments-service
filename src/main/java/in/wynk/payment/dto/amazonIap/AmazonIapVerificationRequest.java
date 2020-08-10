package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.request.IapVerificationRequest;
import lombok.Getter;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
public class AmazonIapVerificationRequest extends IapVerificationRequest {
    @Analysed
    private UserData userData;
    @Analysed
    private Receipt receipt;
}
