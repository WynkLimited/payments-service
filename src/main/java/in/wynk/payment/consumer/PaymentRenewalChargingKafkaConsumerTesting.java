package in.wynk.payment.consumer;


import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.PaymentRenewalChargingMessageTest;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.service.PaymentManager;
import in.wynk.scheduler.queue.constant.BeanConstant;
import in.wynk.stream.constant.StreamConstant;
import in.wynk.stream.constant.StreamMarker;
import in.wynk.stream.consumer.impl.AbstractKafkaEventConsumer;
import in.wynk.stream.service.KafkaRetryHandlerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;


@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class PaymentRenewalChargingKafkaConsumerTesting extends AbstractKafkaEventConsumer<String, PaymentRenewalChargingMessageTest> {

    private final PaymentManager paymentManager;
    private final PaymentGatewayManager manager;
    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    private final KafkaListenerEndpointRegistry endpointRegistry;
    @Autowired
    private KafkaRetryHandlerService<String, PaymentRenewalChargingMessageTest> kafkaRetryHandlerService;

    public PaymentRenewalChargingKafkaConsumerTesting(PaymentManager paymentManager, PaymentGatewayManager manager, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.paymentManager = paymentManager;
        this.manager = manager;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentRenewalChargingMessageLoadTesting")
    public void consume(PaymentRenewalChargingMessageTest message) throws WynkRuntimeException {
        final String messageId = message.getId(); // Assuming message has an ID
        final String clientAlias = message.getClientAlias();

        // 1. Log message receipt with context
        log.info("[Client:{}] Processing payment renewal message ID: {}", clientAlias, messageId);

        try {
            // 2. Validate message (critical for payment systems)
            if (message == null) {
                throw new WynkRuntimeException("Invalid payment renewal message");
            }

            // 3. Update analytics with context
            AnalyticService.update(message);
            log.debug("[Client:{}] Analytics updated for message ID: {}", clientAlias, messageId);

            // 4. Simulate processing with observability
            Instant start = Instant.now();
            try {
                Thread.sleep(600); // Simulate 600ms processing
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); // Restore interrupt flag
                throw new WynkRuntimeException("Processing interrupted for message ID: " + messageId, e);
            }

            // 5. Log processing completion with latency
            Duration processingTime = Duration.between(start, Instant.now());
            log.info("[Client:{}] Successfully processed message ID: {} in {} ms",
                    clientAlias, messageId, processingTime.toMillis());

        } catch (WynkRuntimeException e) {
            // 6. Business logic errors
            log.error("[Client:{}] Failed to process payment renewal message ID: {}. Reason: {}",
                    clientAlias, messageId, e.getMessage(), e);
            throw e; // Re-throw for DLQ handling
        } catch (Exception e) {
            // 7. Unexpected system errors
            log.error("[Client:{}] SYSTEM ERROR processing message ID: {}", clientAlias, messageId, e);
            throw new WynkRuntimeException("Technical failure processing message", e);
        }
    }

    @KafkaListener(id = "paymentRenewalChargingMessageLoadTestingListener", topics = "${wynk.kafka.consumers.listenerFactory.paymentRenewalChargingLoadTesting[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.paymentRenewalCharging[0].name}")
    protected void listenPaymentRenewalChargingMessageLoadTesting(@Header(value = StreamConstant.MESSAGE_LAST_ATTEMPTED_SEQUENCE, required = false) String lastAttemptedSequence,
                                                       @Header(value = StreamConstant.MESSAGE_CREATION_DATETIME, required = false) String createdAt,
                                                       @Header(value = StreamConstant.MESSAGE_LAST_PROCESSED_DATETIME, required = false) String lastProcessedAt,
                                                       @Header(value = StreamConstant.RETRY_COUNT, required = false) String retryCount,
                                                       ConsumerRecord<String, PaymentRenewalChargingMessageTest> consumerRecord, Acknowledgment acknowledgment) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            kafkaRetryHandlerService.retry(consumerRecord, lastAttemptedSequence, createdAt, lastProcessedAt, retryCount);
            if (!(e instanceof WynkRuntimeException)) {
                log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Something went wrong while processing message {} for kafka consumer : {}", consumerRecord.value(), ", PaymentRenewalChargingMessage - ", e);
            }
        } finally {
            acknowledgment.acknowledge();
        }
    }

    @Override
    public void start() {
        if (enabled) {
            log.info("Starting Kafka consumption..." + this.getClass().getCanonicalName());
        }
    }

    @Override
    public void stop() {
        if (enabled) {
            log.info("Shutting down Kafka consumption..." + this.getClass().getCanonicalName());
            this.endpointRegistry.stop();
        }
    }
}
