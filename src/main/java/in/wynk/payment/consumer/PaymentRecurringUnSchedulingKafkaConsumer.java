package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.common.messages.PaymentRecurringUnSchedulingMessage;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.RecurringPaymentEvent;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.PaymentRefundInitMessage;
import in.wynk.payment.dto.PaymentRefundInitRequest;
import in.wynk.payment.dto.aps.common.ApsConstant;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ITransactionManagerService;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.DependsOn;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Objects;

import static in.wynk.payment.core.constant.PaymentConstants.ADD_TO_BILL;

@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class PaymentRecurringUnSchedulingKafkaConsumer extends AbstractKafkaEventConsumer<String, PaymentRecurringUnSchedulingMessage> {

    private final IRecurringPaymentManagerService recurringPaymentManager;
    private final ITransactionManagerService transactionManagerService;
    private final ApplicationEventPublisher eventPublisher;
    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    private final KafkaListenerEndpointRegistry endpointRegistry;
    @Autowired
    private KafkaRetryHandlerService<String, PaymentRecurringUnSchedulingMessage> kafkaRetryHandlerService;

    public PaymentRecurringUnSchedulingKafkaConsumer (IRecurringPaymentManagerService recurringPaymentManager, ApplicationEventPublisher eventPublisher, ITransactionManagerService transactionManagerService, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.recurringPaymentManager = recurringPaymentManager;
        this.eventPublisher = eventPublisher;
        this.transactionManagerService = transactionManagerService;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentRecurringUnSchedulingMessage")
    public void consume (PaymentRecurringUnSchedulingMessage message) {
        AnalyticService.update(message);
        final Transaction transaction = transactionManagerService.get(message.getTransactionId());
        if (Objects.nonNull(transaction) && transaction.getPaymentChannel().getId().equalsIgnoreCase(ADD_TO_BILL)) {
            eventPublisher.publishEvent(RecurringPaymentEvent.builder().transaction(transaction).paymentEvent(message.getPaymentEvent()).build());
        } else {
            recurringPaymentManager.unScheduleRecurringPayment(transaction, message.getPaymentEvent(), message.getValidUntil(), message.getDeferredUntil());
        }
    }

    @KafkaListener(id = "paymentRecurringUnSchedulingMessageListener", topics = "${wynk.kafka.consumers.listenerFactory.paymentRecurringUnScheduling[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.paymentRecurringUnScheduling[0].name}")
    protected void listenPaymentRecurringUnSchedulingMessage(@Header(BeanConstant.MESSAGE_LAST_ATTEMPTED_SEQUENCE) String lastAttemptedSequence,
                                                             @Header(BeanConstant.MESSAGE_CREATION_DATETIME) String createdAt,
                                                             @Header(BeanConstant.MESSAGE_LAST_PROCESSED_DATETIME) String lastProcessedAt,
                                                             @Header(StreamConstant.RETRY_COUNT) String retryCount,
                                                             ConsumerRecord<String, PaymentRecurringUnSchedulingMessage> consumerRecord) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            kafkaRetryHandlerService.retry(consumerRecord, lastAttemptedSequence, createdAt, lastProcessedAt, retryCount);
            if (!(e instanceof WynkRuntimeException)) {
                log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Something went wrong while processing message {} for kafka consumer : {}", consumerRecord.value(), ", PaymentRecurringUnSchedulingMessage - ", e);
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