package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.dto.aps.kafka.PayChargeReqMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;

import static in.wynk.stream.constant.StreamMarker.KAFKA_CONSUMPTION_ERROR;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class PaymentChargeCallbackKafkaSerializer implements Deserializer<PayChargeReqMessage> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PayChargeReqMessage deserialize(String topic, byte[] bytes) {
        try {
            PayChargeReqMessage message = objectMapper.readValue(bytes, PayChargeReqMessage.class);
            log.info("response received for payment charge callback - " + message);
            return message;
        } catch (Exception e) {
            log.error(KAFKA_CONSUMPTION_ERROR, "Error in deserializing the payload {} for payment charge", e.getMessage(), e);
        }
        return null;
    }
}
