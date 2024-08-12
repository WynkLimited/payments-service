package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.service.PaymentGatewayManager;
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
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.Objects;

import static in.wynk.common.enums.PaymentEvent.*;

@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class PreDebitNotificationKafkaConsumer extends AbstractKafkaEventConsumer<String, PreDebitNotificationMessage> {

    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    private final PaymentGatewayManager manager;
    private final KafkaListenerEndpointRegistry endpointRegistry;
    @Autowired
    private KafkaRetryHandlerService<String, PreDebitNotificationMessage> kafkaRetryHandlerService;

    public PreDebitNotificationKafkaConsumer (PaymentGatewayManager manager, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.manager = manager;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "preDebitNotificationMessage")
    public void consume(PreDebitNotificationMessage message) {
        AnalyticService.update(message);
        manager.notify(message);
    }

    @KafkaListener(id = "preDebitNotificationMessageManagerListener", topics = "${wynk.kafka.consumers.listenerFactory.preDebitNotification[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.preDebitNotification[0].name}")
    protected void listenPreDebitNotificationMessageManager(@Header(value = StreamConstant.MESSAGE_LAST_ATTEMPTED_SEQUENCE, required = false) String lastAttemptedSequence,
                                                            @Header(value = StreamConstant.MESSAGE_CREATION_DATETIME, required = false) String createdAt,
                                                            @Header(value = StreamConstant.MESSAGE_LAST_PROCESSED_DATETIME, required = false) String lastProcessedAt,
                                                            @Header(value = StreamConstant.RETRY_COUNT, required = false) String retryCount,
                                                            ConsumerRecord<String, PreDebitNotificationMessage> consumerRecord) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            kafkaRetryHandlerService.retry(consumerRecord, lastAttemptedSequence, createdAt, lastProcessedAt, retryCount);
            if (!(e instanceof WynkRuntimeException)) {
                log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Something went wrong while processing message {} for kafka consumer : {}", consumerRecord.value(), ", PreDebitNotificationMessage - ", e);
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