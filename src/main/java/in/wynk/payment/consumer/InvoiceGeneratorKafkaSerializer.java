package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.dto.invoice.GenerateInvoiceEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import static in.wynk.stream.constant.StreamMarker.KAFKA_CONSUMPTION_ERROR;

@Slf4j
public class InvoiceGeneratorKafkaSerializer implements Deserializer<GenerateInvoiceEvent> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GenerateInvoiceEvent deserialize(String topic, byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, GenerateInvoiceEvent.class);
        } catch (Exception e) {
            log.error(KAFKA_CONSUMPTION_ERROR, "Error in deserializing the payload", e);
        }
        return null;
    }
}

