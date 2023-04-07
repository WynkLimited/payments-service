package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.dto.SessionDTO;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.payu.VerificationType;
import in.wynk.session.context.SessionContextHolder;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import static in.wynk.common.constant.BaseConstants.CLIENT;

@Getter
@ToString
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public abstract class AbstractVerificationRequest {

    @NotBlank
    @Analysed
    private String verifyValue;

    @NotNull
    private String paymentCode;

    @NotNull
    @Analysed
    private VerificationType verificationType;

    public abstract String getMsisdn();

    public PaymentGateway getPaymentCode() {
        return PaymentCodeCachingService.getFromPaymentCode(this.paymentCode);
    }

    public String getClient() {
        return SessionContextHolder.<SessionDTO>getBody().get(CLIENT);
    }

}