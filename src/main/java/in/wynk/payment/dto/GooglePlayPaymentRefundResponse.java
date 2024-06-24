package in.wynk.payment.dto;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import lombok.Getter;
import lombok.experimental.SuperBuilder;


/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public class GooglePlayPaymentRefundResponse extends AbstractPaymentRefundResponse {
    @Override
    public PaymentGateway getPaymentGateway () {
        return PaymentCodeCachingService.getFromPaymentCode(BeanConstant.GOOGLE_PLAY);
    }

    // This id will always be null for Google Play as google return empty body if refund was success
    @Override
    public String getExternalReferenceId () {
        return null;
    }
}
