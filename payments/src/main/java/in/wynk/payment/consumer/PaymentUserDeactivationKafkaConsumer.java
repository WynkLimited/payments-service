package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.common.messages.PaymentUserDeactivationMessage;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.event.PaymentUserDeactivationEvent;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.scheduler.queue.constant.BeanConstant;
import in.wynk.stream.constant.StreamConstant;
import in.wynk.stream.constant.StreamMarker;
import in.wynk.stream.consumer.impl.AbstractKafkaEventConsumer;
import in.wynk.stream.service.KafkaRetryHandlerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.DependsOn;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class PaymentUserDeactivationKafkaConsumer extends AbstractKafkaEventConsumer<String, PaymentUserDeactivationMessage> {

    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    private final ApplicationEventPublisher eventPublisher;
    private final KafkaListenerEndpointRegistry endpointRegistry;
    @Autowired
    private KafkaRetryHandlerService<String, PaymentUserDeactivationMessage> kafkaRetryHandlerService;

    public PaymentUserDeactivationKafkaConsumer (ApplicationEventPublisher eventPublisher, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.eventPublisher = eventPublisher;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentUserDeactivationMessage")
    public void consume(PaymentUserDeactivationMessage message) {
        log.info(PaymentLoggingMarker.USER_DEACTIVATION_SUBSCRIPTION_QUEUE, "processing PaymentUserDeactivationMessage for uid {} and id {}", message.getUid(), message.getId());
        AnalyticService.update(message);
        //transactionManagerService.migrateOldTransactions(message.getId(), message.getUid(), message.getOldUid());
        eventPublisher.publishEvent(
                PaymentUserDeactivationEvent.builder().id(message.getId()).uid(message.getUid()).oldUid(message.getOldUid()).clientAlias(message.getClientAlias()).service(message.getService()).build());
    }

    @KafkaListener(id = "paymentUserDeactivationMessageListener", topics = "${wynk.kafka.consumers.listenerFactory.paymentUserDeactivation[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.paymentUserDeactivation[0].name}")
    protected void listenPaymentUserDeactivationMessage(@Header(value = StreamConstant.MESSAGE_LAST_ATTEMPTED_SEQUENCE, required = false) String lastAttemptedSequence,
                                                        @Header(value = StreamConstant.MESSAGE_CREATION_DATETIME, required = false) String createdAt,
                                                        @Header(value = StreamConstant.MESSAGE_LAST_PROCESSED_DATETIME, required = false) String lastProcessedAt,
                                                        @Header(value = StreamConstant.RETRY_COUNT, required = false) String retryCount,
                                                        ConsumerRecord<String, PaymentUserDeactivationMessage> consumerRecord) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            kafkaRetryHandlerService.retry(consumerRecord, lastAttemptedSequence, createdAt, lastProcessedAt, retryCount);
            if (!(e instanceof WynkRuntimeException)) {
                log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Something went wrong while processing message {} for kafka consumer : {}", consumerRecord.value(), ", PaymentUserDeactivationMessage - ", e);
            }
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