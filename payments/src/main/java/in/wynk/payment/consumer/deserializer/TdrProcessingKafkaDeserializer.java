package in.wynk.payment.consumer.deserializer;

import in.wynk.payment.dto.TdrProcessingMessage;
import lombok.extern.slf4j.Slf4j;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.dto.PreDebitNotificationMessageManager;
import org.apache.kafka.common.serialization.Deserializer;

import static in.wynk.stream.constant.StreamMarker.KAFKA_CONSUMPTION_ERROR;

@Slf4j
public class TdrProcessingKafkaDeserializer implements Deserializer<TdrProcessingMessage> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public TdrProcessingMessage deserialize(String topic, byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, TdrProcessingMessage.class);
        } catch (Exception e) {
            log.error(KAFKA_CONSUMPTION_ERROR, "Error in deserializing the payload {}", e.getMessage(), e);
        }
        return null;
    }
}
