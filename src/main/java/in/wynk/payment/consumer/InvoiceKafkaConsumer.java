package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.invoice.GenerateInvoiceEvent;
import in.wynk.payment.dto.invoice.InvoiceCallbackEvent;
import in.wynk.payment.dto.invoice.InvoiceEvent;
import in.wynk.stream.constant.StreamMarker;
import in.wynk.stream.consumer.impl.AbstractKafkaEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class InvoiceKafkaConsumer extends AbstractKafkaEventConsumer<String, InvoiceEvent> {

    private final InvoiceHandler<InvoiceEvent> invoiceHandler;

    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;

    private final KafkaListenerEndpointRegistry endpointRegistry;

    public InvoiceKafkaConsumer (InvoiceHandler<InvoiceEvent> invoiceHandler, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.invoiceHandler = invoiceHandler;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    public void consume(InvoiceEvent event) throws WynkRuntimeException {
        if(GenerateInvoiceEvent.class.isAssignableFrom(event.getClass())){
            invoiceHandler.generateInvoice(event);
        } else if (InvoiceCallbackEvent.class.isAssignableFrom(event.getClass())) {
            invoiceHandler.processCallback(event);
        }
    }

    @KafkaListener(id = "invoiceListener", topics = "${wynk.kafka.consumers.listenerFactory.discovery[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.discovery[0].name}")
    @AnalyseTransaction(name = "generateInvoiceKafkaListener")
    protected void listenGenerateInvoice(ConsumerRecord<String, GenerateInvoiceEvent> consumerRecord) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Error occurred in polling/consuming kafka event", e);
        }
    }

    @KafkaListener(id = "invoiceListener", topics = "${wynk.kafka.consumers.listenerFactory.discovery[1].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.discovery[1].name}")
    @AnalyseTransaction(name = "invoiceCallbackKafkaListener")
    protected void listenInvoiceCallback(ConsumerRecord<String, InvoiceCallbackEvent> consumerRecord) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Error occurred in polling/consuming kafka event", e);
        }
    }

    @Override
    public void start() {
        if (enabled) {
            log.info("Starting Kafka consumption...");
        }
    }

    @Override
    public void stop() {
        if (enabled) {
            log.info("Shutting down Kafka consumption...");
            this.endpointRegistry.stop();
        }
    }
}