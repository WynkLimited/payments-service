package in.wynk.payment.dto.phonepe;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import static in.wynk.payment.constant.WalletConstants.PHONEPE_WALLET;

@Getter
@SuperBuilder
@AnalysedEntity
public class PhonePePaymentRefundResponse extends AbstractPaymentRefundResponse {

    @Analysed
    private final String providerReferenceId;

    @Override
    public PaymentGateway getPaymentGateway() {
        return PaymentCodeCachingService.getFromPaymentCode(PHONEPE_WALLET);
    }

    @Override
    public String getExternalReferenceId() {
        return providerReferenceId;
    }

}