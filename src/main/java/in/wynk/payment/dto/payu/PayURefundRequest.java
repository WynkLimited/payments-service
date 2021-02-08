package in.wynk.payment.dto.payu;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.request.AbstractRefundRequest;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
@AnalysedEntity
public class PayURefundRequest extends AbstractRefundRequest {
    private String authPayUId;
}
