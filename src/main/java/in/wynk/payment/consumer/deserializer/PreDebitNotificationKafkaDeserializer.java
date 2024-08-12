package in.wynk.payment.consumer.deserializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;

import static in.wynk.stream.constant.StreamMarker.KAFKA_CONSUMPTION_ERROR;


@Slf4j
public class PreDebitNotificationKafkaDeserializer implements Deserializer<PreDebitNotificationMessage> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PreDebitNotificationMessage deserialize(String topic, byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, PreDebitNotificationMessage.class);
        } catch (Exception e) {
            log.error(KAFKA_CONSUMPTION_ERROR, "Error in deserializing the payload {}", e.getMessage(), e);
        }
        return null;
    }
}