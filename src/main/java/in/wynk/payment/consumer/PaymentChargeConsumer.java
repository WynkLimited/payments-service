package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.constants.LoggingConstants;
import in.wynk.payment.dto.aps.kafka.PayChargeReqMessage;
import in.wynk.payment.dto.aps.kafka.PaymentChargeRequestMessage;
import in.wynk.stream.constant.StreamMarker;
import in.wynk.stream.consumer.impl.AbstractKafkaEventConsumer;
import in.wynk.stream.producer.IKafkaEventPublisher;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static in.wynk.common.constant.BaseConstants.*;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class PaymentChargeConsumer extends AbstractKafkaEventConsumer<String, PaymentChargeRequestMessage> {

    private final PaymentChargeHandler paymentChargeHandler;
    private final KafkaListenerEndpointRegistry endpointRegistry;
    private final ScheduledExecutorService scheduler;
    private final IKafkaEventPublisher kafkaEventPublisher;
    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    @Value("${wynk.kafka.producers.payment.retry.topic}")
    private String kafkaRetryTopic;
    @Value("${wynk.kafka.producers.payment.dlt.topic}")
    private String kafkaDLTTopic;

    public PaymentChargeConsumer (PaymentChargeHandler paymentChargeHandler, KafkaListenerEndpointRegistry endpointRegistry, IKafkaEventPublisher kafkaEventPublisher) {
        super();
        this.paymentChargeHandler = paymentChargeHandler;
        this.endpointRegistry = endpointRegistry;
        this.kafkaEventPublisher = kafkaEventPublisher;
        this.scheduler = Executors.newScheduledThreadPool(3);
        ;
    }


    @KafkaListener(id = "paymentChargeListener", topics = "${wynk.kafka.consumers.listenerFactory.payment.charge[0].factoryDetails.topic}",
            containerFactory = "${wynk.kafka.consumers.listenerFactory.payment.charge[0].name}")
    @AnalyseTransaction(name = "paymentChargeConsumer")
    protected void listenPaymentCharge (@Header(ORG_ID) String orgId, @Header(SERVICE_ID) String serviceId, @Header(SESSION_ID) String sessionId, @Header(REQUEST_ID) String requestId,
                                        ConsumerRecord<String, PayChargeReqMessage> consumerRecord) {
        try {
            MDC.put(LoggingConstants.REQUEST_ID, requestId);
            PaymentChargeRequestMessage requestMessage = PaymentChargeRequestMessage.builder().message(consumerRecord.value())
                    .requestId(requestId).orgId(orgId).serviceId(serviceId).sessionId(sessionId).build();
            consume(requestMessage);
        } catch (Exception e) {
            log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Error occurred in polling/consuming kafka event", e);
            scheduleRetry(kafkaRetryTopic, orgId, serviceId, sessionId, requestId, consumerRecord, "0");
        } finally {
            MDC.clear();
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
    public void consume (PaymentChargeRequestMessage requestMessage) throws WynkRuntimeException {
        paymentChargeHandler.charge(requestMessage);
    }

    private void scheduleRetry (String topic, String orgId, String serviceId, String sessionId, String requestId, ConsumerRecord<String, PayChargeReqMessage> consumerRecord,
                                String retryAttempt) {
        // Schedule a retry with backoff delay
        scheduler.schedule(() -> {
            // Send the failed message to the retry-topic
            publishEventInKafka(topic, orgId, serviceId, sessionId, requestId, consumerRecord, retryAttempt);
        }, IN_MEMORY_CACHE_CRON, TimeUnit.MILLISECONDS);
    }

    private void publishEventInKafka (String topic, String orgId, String serviceId, String sessionId, String requestId, ConsumerRecord<String, PayChargeReqMessage> consumerRecord,
                                      String retryAttempt) {
        try {
            final RecordHeaders headers = new RecordHeaders();
            headers.add(new RecordHeader(ORG_ID, orgId.getBytes()));
            headers.add(new RecordHeader(SERVICE_ID, serviceId.getBytes()));
            headers.add(new RecordHeader(SESSION_ID, sessionId.getBytes()));
            headers.add(new RecordHeader(REQUEST_ID, requestId.getBytes()));
            headers.add(new RecordHeader(KAFKA_RETRY_COUNT, retryAttempt.getBytes()));
            kafkaEventPublisher.publish(topic, null, null, null, consumerRecord.value(), headers);
        } catch (Exception ignored) {
        }
    }

    @KafkaListener(id = "paymentChargeRetryMessageListener", topics = "${wynk.kafka.consumers.listenerFactory.payment.charge[1].factoryDetails.topic}",
            containerFactory = "${wynk.kafka.consumers.listenerFactory.payment.charge[1].name}")
    @AnalyseTransaction(name = "paymentChargeRetryMessage")
    protected void listenPaymentChargeMessage (@Header(ORG_ID) String orgId, @Header(SERVICE_ID) String serviceId, @Header(SESSION_ID) String sessionId,
                                               @Header(REQUEST_ID) String requestId, @Header(KAFKA_RETRY_COUNT) String retryCount,
                                               ConsumerRecord<String, PayChargeReqMessage> consumerRecord) {
        try {
            if (Objects.nonNull(consumerRecord.value()) && !StringUtils.isEmpty(retryCount)) {
                int retryAttempt = Integer.parseInt(retryCount);
                if (retryAttempt < 3) {
                    final PaymentChargeRequestMessage requestMessage = PaymentChargeRequestMessage.builder().message(consumerRecord.value())
                            .requestId(requestId).orgId(orgId).serviceId(serviceId).sessionId(sessionId).build();
                    consume(requestMessage);
                } else {
                    publishEventInKafka(kafkaDLTTopic, orgId, serviceId, sessionId, requestId, consumerRecord, String.valueOf(Integer.parseInt(retryCount) + 1));
                    log.info(StreamMarker.KAFKA_RETRY_EXHAUSTION_ERROR, "Event from topic is dead lettered - event:" + consumerRecord.value());
                }
            }
        } catch (Exception e) {
            log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Error occurred in polling/consuming kafka event", e);
            scheduleRetry(kafkaRetryTopic, orgId, serviceId, sessionId, requestId, consumerRecord, String.valueOf(Integer.parseInt(retryCount) + 1));
        }
    }
}
