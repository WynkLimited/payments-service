package in.wynk.payment.consumer.deserializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.common.messages.PaymentRecurringUnSchedulingMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;

import static in.wynk.stream.constant.StreamMarker.KAFKA_CONSUMPTION_ERROR;

@Slf4j
public class PaymentRecurringUnSchedulingKafkaDeserializer implements Deserializer<PaymentRecurringUnSchedulingMessage> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PaymentRecurringUnSchedulingMessage deserialize(String topic, byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, PaymentRecurringUnSchedulingMessage.class);
        } catch (Exception e) {
            log.error(KAFKA_CONSUMPTION_ERROR, "Error in deserializing the payload {}", e.getMessage(), e);
        }
        return null;
    }
}