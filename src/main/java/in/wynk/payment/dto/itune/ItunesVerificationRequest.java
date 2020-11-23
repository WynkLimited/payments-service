package in.wynk.payment.dto.itune;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.request.IapVerificationRequest;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@AnalysedEntity
@SuperBuilder
@NoArgsConstructor
public class ItunesVerificationRequest extends IapVerificationRequest {

    @Analysed
    private String receipt;

    @Override
    public PaymentCode getPaymentCode() {
        return PaymentCode.ITUNES;
    }
}
