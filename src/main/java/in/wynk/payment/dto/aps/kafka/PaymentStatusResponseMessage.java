package in.wynk.payment.dto.aps.kafka;

import com.github.annotation.analytic.core.annotations.AnalysedEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.stream.advice.KafkaEvent;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@AnalysedEntity
@RequiredArgsConstructor
@KafkaEvent(topic = "${wynk.kafka.producers.payment.status.topic}")
public class PaymentStatusResponseMessage {
    private final String transactionId;
    private final TransactionStatus status;
    private final PaymentEvent event;
    private final int planId;
    private final String pgCode;
    private final Number amount;
    private final double discountedAmount;
    private final String failureReason;
}
