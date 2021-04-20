package in.wynk.payment.dto.payu;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class PayUPaymentRefundResponse extends AbstractPaymentRefundResponse {

    @Analysed
    private final String requestId;
    @Analysed
    private final String authPayUId;

    @Override
    public PaymentCode getPaymentCode() {
        return PaymentCode.PAYU;
    }

    @Override
    public String getExternalReferenceId() {
        return requestId;
    }
}
