package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AnalysedEntity
public class PaymentErrorEvent {
    private String id;
    private String code;
    private String description;
}
