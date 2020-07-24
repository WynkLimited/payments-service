package in.wynk.payment.core.event;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
@AnalysedEntity
public class MerchantTransactionEvent {
    private String id;
    private String externalTransactionId;
    private Object request;
    private Object response;
}
