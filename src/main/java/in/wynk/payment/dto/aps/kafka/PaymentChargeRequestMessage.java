package in.wynk.payment.dto.aps.kafka;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import lombok.*;

/**
 * @author Nishesh Pandey
 */
@Getter
@ToString
@Builder
@AnalysedEntity
public class PaymentChargeRequestMessage {
    private PayChargeReqMessage message;
    private String orgId;
    private String sessionId;
    private String serviceId;
    private String requestId;
}
