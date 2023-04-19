package in.wynk.payment.dto.response.paytm;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import static in.wynk.payment.constant.WalletConstants.PAYTM_WALLET;

@Getter
@SuperBuilder
@AnalysedEntity
public class PaytmPaymentRefundResponse extends AbstractPaymentRefundResponse {

    @Analysed
    private final String paytmTxnId;

    @Override
    public PaymentGateway getPaymentGateway() {
        return PaymentCodeCachingService.getFromPaymentCode(PAYTM_WALLET);
    }

    @Override
    public String getExternalReferenceId() {
        return paytmTxnId;
    }

}