package in.wynk.payment.dto.gpbs.acknowledge.request;

import com.github.annotation.analytic.core.annotations.Analysed;
import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.core.dao.entity.PaymentGateway;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
public abstract class AbstractPaymentAcknowledgementRequest {
    @Analysed
    private final PaymentGateway paymentGateway;
}
