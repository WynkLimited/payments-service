package in.wynk.payment.dto.itune;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.request.IapVerificationRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;

import static in.wynk.payment.core.constant.PaymentConstants.ITUNES;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ItunesVerificationRequest extends IapVerificationRequest {

    @NotBlank
    @Analysed
    private String receipt;

    @Override
    public PaymentGateway getPaymentGateway() {
        return PaymentCodeCachingService.getFromPaymentCode(ITUNES);
    }

}