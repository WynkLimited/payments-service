package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.IapVerificationRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
@AnalysedEntity
@SuperBuilder
@NoArgsConstructor
public class AmazonIapVerificationRequest extends IapVerificationRequest {
    @Analysed
    private UserData userData;
    @Analysed
    private Receipt receipt;

    @Override
    public PaymentCode getPaymentCode() {
        return PaymentCode.AMAZON_IAP;
    }
}
