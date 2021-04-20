package in.wynk.payment.dto.phonepe;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class PhonePePaymentRefundResponse extends AbstractPaymentRefundResponse {

    @Analysed
    private final String providerReferenceId;

    @Override
    public PaymentCode getPaymentCode() {
        return PaymentCode.PHONEPE_WALLET;
    }

    @Override
    public String getExternalReferenceId() {
        return providerReferenceId;
    }
}
