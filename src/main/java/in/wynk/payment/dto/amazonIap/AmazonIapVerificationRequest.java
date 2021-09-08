package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.IapVerificationRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AmazonIapVerificationRequest extends IapVerificationRequest {

    @Valid
    @Analysed
    private Receipt receipt;

    @Valid
    @Analysed
    private UserData userData;

    @Override
    public PaymentCode getPaymentCode() {
        return PaymentCode.AMAZON_IAP;
    }

}