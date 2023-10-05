package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.aps.kafka.PaymentChargeKafkaMessage;
import in.wynk.payment.dto.request.AbstractPaymentChargingRequest;
import in.wynk.stream.constant.StreamMarker;
import in.wynk.stream.consumer.impl.AbstractKafkaEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.stereotype.Service;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class PaymentChargeConsumer extends AbstractKafkaEventConsumer<String, PaymentChargeKafkaMessage> {

    private final PaymentChargeHandler paymentChargeHandler;

    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;

    private final KafkaListenerEndpointRegistry endpointRegistry;

    public PaymentChargeConsumer (PaymentChargeHandler paymentChargeHandler, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.paymentChargeHandler = paymentChargeHandler;
        this.endpointRegistry = endpointRegistry;
    }


    @KafkaListener(id = "paymentChargeListener", topics = "${wynk.kafka.consumers.listenerFactory.payment.charge[0].factoryDetails.topic}",
            containerFactory = "${wynk.kafka.consumers.listenerFactory.payment.charge[0].name}")
    @AnalyseTransaction(name = "paymentChargeConsumer")
    protected void listenPaymentCharge (ConsumerRecord<String, PaymentChargeKafkaMessage> consumerRecord) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Error occurred in polling/consuming kafka event", e);
        }
    }

    @Override
    public void start () {
        if (enabled) {
            log.info("Starting Kafka consumption...");
        }
    }

    @Override
    public void stop () {
        if (enabled) {
            log.info("Shutting down Kafka consumption...");
            this.endpointRegistry.stop();
        }
    }

    @Override
    public void consume (PaymentChargeKafkaMessage event) throws WynkRuntimeException {
        if (AbstractPaymentChargingRequest.class.isAssignableFrom(event.getClass())) {
            paymentChargeHandler.charge(event);
        }
    }
}
