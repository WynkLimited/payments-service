package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class ApsPaymentRefundResponse extends AbstractPaymentRefundResponse {

    private String requestId;

    @Override
    public PaymentGateway getPaymentGateway() {
        return PaymentCodeCachingService.getFromPaymentCode(PaymentConstants.AIRTEL_PAY_STACK);
    }

    @Override
    public String getExternalReferenceId() {
        return requestId;
    }

}
