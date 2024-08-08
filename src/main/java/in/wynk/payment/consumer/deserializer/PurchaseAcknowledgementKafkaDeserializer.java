package in.wynk.payment.consumer.deserializer;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.dto.gpbs.acknowledge.queue.PurchaseAcknowledgeMessageManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;

import static in.wynk.stream.constant.StreamMarker.KAFKA_CONSUMPTION_ERROR;


@Slf4j
public class PurchaseAcknowledgementKafkaDeserializer implements Deserializer<PurchaseAcknowledgeMessageManager> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public PurchaseAcknowledgeMessageManager deserialize(String topic, byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, PurchaseAcknowledgeMessageManager.class);
        } catch (Exception e) {
            log.error(KAFKA_CONSUMPTION_ERROR, "Error in deserializing the payload {}", e.getMessage(), e);
        }
        return null;
    }
}