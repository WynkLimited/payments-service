package in.wynk.payment.dto.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import javax.validation.constraints.NotNull;

@Getter
@SuperBuilder
@AnalysedEntity
@NoArgsConstructor
@AllArgsConstructor
public abstract class WalletRequest {

    @NotNull
    @Analysed
    private String paymentCode;

    public PaymentGateway getPaymentCode() {
        return PaymentCodeCachingService.getFromPaymentCode(this.paymentCode);
    }

    public abstract String getDeviceId();

    public abstract String getClient();

}