package in.wynk.payment.dto.amazonIap;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.request.IapVerificationRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.Valid;

import static in.wynk.payment.core.constant.PaymentConstants.AMAZON_IAP;

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
    public PaymentGateway getPaymentGateway() {
        return PaymentCodeCachingService.getFromPaymentCode(AMAZON_IAP);
    }

}