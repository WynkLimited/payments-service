package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.dto.aps.kafka.Message;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;

import static in.wynk.stream.constant.StreamMarker.KAFKA_CONSUMPTION_ERROR;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class PaymentChargeCallbackKafkaSerializer implements Deserializer<Message> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Message deserialize(String topic, byte[] bytes) {
        try {
            Message message = objectMapper.readValue(bytes, Message.class);
            log.info("response received for invoice callback - " + message);
            return message;
        } catch (Exception e) {
            log.error(KAFKA_CONSUMPTION_ERROR, "Error in deserializing the payload {}", e.getMessage(), e);
        }
        return null;
    }
}
