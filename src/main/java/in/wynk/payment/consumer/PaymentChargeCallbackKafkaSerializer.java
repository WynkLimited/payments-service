package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.dto.aps.kafka.PaymentChargeRequestMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;

import static in.wynk.stream.constant.StreamMarker.KAFKA_CONSUMPTION_ERROR;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class PaymentChargeCallbackKafkaSerializer implements Deserializer<PaymentChargeRequestMessage> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PaymentChargeRequestMessage deserialize(String topic, byte[] bytes) {
        try {
            String message = objectMapper.readValue(bytes, String.class);
            log.info("response received for invoice callback - " + message);
            return objectMapper.readValue(message, PaymentChargeRequestMessage.class);
        } catch (Exception e) {
            log.error(KAFKA_CONSUMPTION_ERROR, "Error in deserializing the payload {}", e.getMessage(), e);
        }
        return null;
    }
}
