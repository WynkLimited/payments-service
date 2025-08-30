package in.wynk.payment.event;

import in.wynk.common.enums.PaymentEvent;
import in.wynk.stream.advice.KafkaEvent;
import lombok.*;
import lombok.extern.slf4j.Slf4j;

import static in.wynk.logging.BaseLoggingMarkers.KAFKA_MESSAGE_CREATOR_ERROR;

@Getter
@Setter
@Builder
@Slf4j
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
@KafkaEvent(topic = "${wynk.data.platform.topic}", transactionName = "recurringPaymentEvent")
public class RecurringKafkaMessage {

    private String paymentEvent;
    private String paymentCode;
    private String txnId;

    public static RecurringKafkaMessage from(String txnId, PaymentEvent paymentEvent, String paymentCode) {
        try {
            return RecurringKafkaMessage.builder()
                    .txnId(txnId)
                    .paymentCode(paymentCode)
                    .paymentEvent(paymentEvent.name())
                    .build();

        } catch (Exception e) {
            log.error(KAFKA_MESSAGE_CREATOR_ERROR, "Error in creating RecurringKafkaMessage for txnId {}", txnId, e);
        }
        return null;
    }
}
