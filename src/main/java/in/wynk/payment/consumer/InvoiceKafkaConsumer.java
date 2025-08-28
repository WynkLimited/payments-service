package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.invoice.CallbackInvoiceKafkaMessage;
import in.wynk.payment.dto.invoice.GenerateInvoiceKafkaMessage;
import in.wynk.payment.dto.invoice.InvoiceKafkaMessage;
import in.wynk.stream.constant.StreamMarker;
import in.wynk.stream.consumer.impl.AbstractKafkaEventConsumer;
import in.wynk.stream.service.IDataPlatformKafkaService;
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
public class InvoiceKafkaConsumer extends AbstractKafkaEventConsumer<String, InvoiceKafkaMessage> {

    private final InvoiceHandler<InvoiceKafkaMessage> invoiceHandler;

    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;

    private final KafkaListenerEndpointRegistry endpointRegistry;
    private final IDataPlatformKafkaService dataPlatformKafkaService;

    public InvoiceKafkaConsumer (InvoiceHandler<InvoiceKafkaMessage> invoiceHandler, KafkaListenerEndpointRegistry endpointRegistry,IDataPlatformKafkaService dataPlatformKafkaService) {
        super();
        this.invoiceHandler = invoiceHandler;
        this.endpointRegistry = endpointRegistry;
        this.dataPlatformKafkaService = dataPlatformKafkaService;
    }

    @Override
    public void consume(InvoiceKafkaMessage message) throws WynkRuntimeException {
        if(GenerateInvoiceKafkaMessage.class.isAssignableFrom(message.getClass())){
            invoiceHandler.generateInvoice(message);
        } else if (CallbackInvoiceKafkaMessage.class.isAssignableFrom(message.getClass())) {
            invoiceHandler.processCallback(message);
        }
        dataPlatformKafkaService.publish(creator.convert(message,null));
    }

    @KafkaListener(id = "generateInvoiceListener", topics = "${wynk.kafka.consumers.listenerFactory.invoice[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.invoice[0].name}")
    @AnalyseTransaction(name = "generateInvoice")
    protected void listenGenerateInvoice(ConsumerRecord<String, GenerateInvoiceKafkaMessage> consumerRecord) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Error occurred in polling/consuming kafka event", e);
        }
    }

    @KafkaListener(id = "callbackInvoiceListener", topics = "${wynk.kafka.consumers.listenerFactory.invoice[1].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.invoice[1].name}")
    @AnalyseTransaction(name = "callbackInvoice")
    protected void listenInvoiceCallback(ConsumerRecord<String, CallbackInvoiceKafkaMessage> consumerRecord) {
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