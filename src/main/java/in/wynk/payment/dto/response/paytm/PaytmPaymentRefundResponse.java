package in.wynk.payment.dto.response.paytm;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class PaytmPaymentRefundResponse extends AbstractPaymentRefundResponse {

    @Analysed
    private final String paytmTxnId;

    @Override
    public PaymentCode getPaymentCode() {
        return PaymentCode.PAYTM_WALLET;
    }

    @Override
    public String getExternalReferenceId() {
        return paytmTxnId;
    }

}