package in.wynk.payment.consumer.deserializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.common.messages.PaymentUserDeactivationMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;

import static in.wynk.stream.constant.StreamMarker.KAFKA_CONSUMPTION_ERROR;

@Slf4j
public class PaymentUserDeactivationKafkaDeserializer implements Deserializer<PaymentUserDeactivationMessage> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PaymentUserDeactivationMessage deserialize(String topic, byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, PaymentUserDeactivationMessage.class);
        } catch (Exception e) {
            log.error(KAFKA_CONSUMPTION_ERROR, "Error in deserializing the payload {}", e.getMessage(), e);
        }
        return null;
    }
}