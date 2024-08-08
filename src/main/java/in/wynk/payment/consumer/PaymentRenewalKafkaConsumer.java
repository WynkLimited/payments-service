package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.PaymentRenewalMessage;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.utils.RecurringTransactionUtils;
import in.wynk.scheduler.queue.constant.BeanConstant;
import in.wynk.stream.constant.StreamConstant;
import in.wynk.stream.constant.StreamMarker;
import in.wynk.stream.consumer.impl.AbstractKafkaEventConsumer;
import in.wynk.stream.producer.IKafkaPublisherService;
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

@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class PaymentRenewalKafkaConsumer extends AbstractKafkaEventConsumer<String, PaymentRenewalMessage> {

    private final ITransactionManagerService transactionManager;
    private final RecurringTransactionUtils recurringTransactionUtils;
    private final IKafkaPublisherService kafkaPublisherService;
    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    private final KafkaListenerEndpointRegistry endpointRegistry;
    @Autowired
    private KafkaRetryHandlerService<String, PaymentRenewalMessage> kafkaRetryHandlerService;

    public PaymentRenewalKafkaConsumer (ITransactionManagerService transactionManager, RecurringTransactionUtils recurringTransactionUtils, IKafkaPublisherService kafkaPublisherService, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.transactionManager = transactionManager;
        this.recurringTransactionUtils = recurringTransactionUtils;
        this.kafkaPublisherService = kafkaPublisherService;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentRenewalMessage")
    public void consume(PaymentRenewalMessage message) throws WynkRuntimeException {
        AnalyticService.update(message);
        log.info(PaymentLoggingMarker.PAYMENT_RENEWAL_QUEUE, "processing PaymentRenewalMessage for transactionId {}", message.getTransactionId());
        Transaction transaction = transactionManager.get(message.getTransactionId());
        if (recurringTransactionUtils.isEligibleForRenewal(transaction, false)) {
            kafkaPublisherService.publishKafkaMessage(PaymentRenewalChargingMessage.builder()
                    .uid(transaction.getUid())
                    .id(transaction.getIdStr())
                    .planId(transaction.getPlanId())
                    .msisdn(transaction.getMsisdn())
                    .clientAlias(transaction.getClientAlias())
                    .attemptSequence(message.getAttemptSequence())
                    .paymentCode(transaction.getPaymentChannel().getId())
                    .build());
        }
    }

    @KafkaListener(id = "paymentRenewalMessageListener", topics = "${wynk.kafka.consumers.listenerFactory.paymentRenewal[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.paymentRenewal[0].name}")
    protected void listenPaymentRenewalMessage(@Header(BeanConstant.MESSAGE_LAST_ATTEMPTED_SEQUENCE) String lastAttemptedSequence,
                                               @Header(BeanConstant.MESSAGE_CREATION_DATETIME) String createdAt,
                                               @Header(BeanConstant.MESSAGE_LAST_PROCESSED_DATETIME) String lastProcessedAt,
                                               @Header(StreamConstant.RETRY_COUNT) String retryCount,
                                               ConsumerRecord<String, PaymentRenewalMessage> consumerRecord) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            kafkaRetryHandlerService.retry(consumerRecord, lastAttemptedSequence, createdAt, lastProcessedAt, retryCount);
            if (!(e instanceof WynkRuntimeException)) {
                log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Something went wrong while processing message {} for kafka consumer : {}", consumerRecord.value(), ", PaymentRenewalMessage - ", e);
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