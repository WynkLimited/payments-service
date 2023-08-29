package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.dto.invoice.CallbackInvoiceKafkaMessage;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Deserializer;
import static in.wynk.stream.constant.StreamMarker.KAFKA_CONSUMPTION_ERROR;

@Slf4j
public class InvoiceCallbackKafkaSerializer implements Deserializer<CallbackInvoiceKafkaMessage> {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public CallbackInvoiceKafkaMessage deserialize(String topic, byte[] bytes) {
        try {
            return objectMapper.readValue(bytes, CallbackInvoiceKafkaMessage.class);
        } catch (Exception e) {
            log.error(KAFKA_CONSUMPTION_ERROR, "Error in deserializing the payload {}", e.getMessage(), e);
        }
        return null;
    }
}