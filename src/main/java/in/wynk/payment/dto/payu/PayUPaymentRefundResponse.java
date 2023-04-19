package in.wynk.payment.dto.payu;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import static in.wynk.payment.core.constant.PaymentConstants.PAYU;

@Getter
@SuperBuilder
@AnalysedEntity
public class PayUPaymentRefundResponse extends AbstractPaymentRefundResponse {

    @Analysed
    private final String requestId;
    @Analysed
    private final String authPayUId;

    @Override
    public PaymentGateway getPaymentGateway() {
        return PaymentCodeCachingService.getFromPaymentCode(PAYU);
    }

    @Override
    public String getExternalReferenceId() {
        return requestId;
    }

}