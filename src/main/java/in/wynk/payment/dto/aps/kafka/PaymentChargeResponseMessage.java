package in.wynk.payment.dto.aps.kafka;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.payment.dto.aps.kafka.response.OrderDetails;
import in.wynk.payment.dto.aps.kafka.response.PlanDetails;
import in.wynk.stream.advice.KafkaEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@RequiredArgsConstructor
@KafkaEvent(topic = "${wynk.kafka.producer.payment.charge.topic}")
public class PaymentChargeResponseMessage {
    private String to;
    private String from;
    @Setter
    private String orgId;
    @Setter
    private String serviceId;
    private String sessionId;
    private String retailerId;
    private String deeplink;
    private String campaignId;
    private PlanDetails planDetails;
    private OrderDetails orderDetails;
}
