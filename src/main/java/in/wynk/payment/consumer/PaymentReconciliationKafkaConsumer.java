package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.PaymentRefundInitEvent;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.gateway.IPaymentStatus;
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
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.*;

@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class PaymentReconciliationKafkaConsumer extends AbstractKafkaEventConsumer<String, PaymentReconciliationMessage> {

    private static final int THREAD_POOL_SIZE = 100;
    private static final int QUEUE_SIZE = 10000;
    private final ScheduledExecutorService scheduler;
    private final ThreadPoolExecutor processorPool;
    private final ITransactionManagerService transactionManager;
    private final ApplicationEventPublisher eventPublisher;
    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    private final KafkaListenerEndpointRegistry endpointRegistry;
    @Autowired
    private PaymentCodeCachingService codeCache;
    @Autowired
    private KafkaRetryHandlerService<String, PaymentReconciliationMessage> kafkaRetryHandlerService;

    public PaymentReconciliationKafkaConsumer (ITransactionManagerService transactionManager, ApplicationEventPublisher eventPublisher, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.transactionManager = transactionManager;
        this.eventPublisher = eventPublisher;
        this.endpointRegistry = endpointRegistry;
        this.scheduler = Executors.newScheduledThreadPool(THREAD_POOL_SIZE);
        this.processorPool =  new ThreadPoolExecutor(THREAD_POOL_SIZE, THREAD_POOL_SIZE,
                0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(QUEUE_SIZE));
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentReconciliation")
    public void consume(PaymentReconciliationMessage message) {
        Transaction transaction = transactionManager.get(message.getTransactionId());
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
            AnalyticService.update(message);
            log.info(PaymentLoggingMarker.PAYMENT_RECONCILIATION_QUEUE, "processing PaymentReconciliationMessage for uid {} and transactionId {}", message.getUid(), message.getTransactionId());
            final AbstractTransactionReconciliationStatusRequest transactionStatusRequest;
            if (message.getPaymentEvent() == PaymentEvent.REFUND) {
                transactionStatusRequest = RefundTransactionReconciliationStatusRequest.builder()
                        .extTxnId(message.getExtTxnId())
                        .transactionId(message.getTransactionId())
                        .build();
            } else if (message.getPaymentEvent() == PaymentEvent.RENEW) {
                transactionStatusRequest = RenewalChargingTransactionReconciliationStatusRequest.builder()
                        .extTxnId(message.getExtTxnId())
                        .transactionId(message.getTransactionId())
                        .originalTransactionId(message.getOriginalTransactionId())
                        .originalAttemptSequence(message.getOriginalAttemptSequence())
                        .build();
            } else {
                transactionStatusRequest = ChargingTransactionReconciliationStatusRequest.builder()
                        .extTxnId(message.getExtTxnId())
                        .transactionId(message.getTransactionId())
                        .build();
            }

            final PaymentReconciliationKafkaConsumer.InnerPaymentStatusDelegator delegate = (AbstractTransactionStatusRequest) -> {
                final boolean canSupportRecon = BeanLocatorFactory.containsBeanOfType(codeCache.get(message.getPaymentCode()).getCode(), new ParameterizedTypeReference<IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>>() {
                });
                if (canSupportRecon) BeanLocatorFactory.getBean(PaymentGatewayManager.class).reconcile(transactionStatusRequest);
                else BeanLocatorFactory.getBean(PaymentManager.class).status(transactionStatusRequest);
            };
            delegate.reconcile(transactionStatusRequest);
        } else if (EnumSet.of(TransactionStatus.SUCCESS).contains(transaction.getStatus()) && transaction.getPaymentChannel().isTrialRefundSupported() && (EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION, PaymentEvent.MANDATE).contains(transaction.getType()))) {
            eventPublisher.publishEvent(PaymentRefundInitEvent.builder()
                    .reason("trial plan amount refund")
                    .originalTransactionId(transaction.getIdStr())
                    .build());
        }
    }

    @AnalyseTransaction(name = "logReconciliationMetrics")
    @KafkaListener(id = "paymentReconciliationMessageListener", topics = "${wynk.kafka.consumers.listenerFactory.paymentReconciliation[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.paymentReconciliation[0].name}")
    protected void listenPaymentReconciliationMessage(@Header(value = StreamConstant.MESSAGE_LAST_ATTEMPTED_SEQUENCE, required = false) String lastAttemptedSequence,
                                                      @Header(value = StreamConstant.MESSAGE_CREATION_DATETIME, required = false) String createdAt,
                                                      @Header(value = StreamConstant.MESSAGE_LAST_PROCESSED_DATETIME, required = false) String lastProcessedAt,
                                                      @Header(value = StreamConstant.RETRY_COUNT, required = false) String retryCount,
                                                      @Header(value = StreamConstant.KAFKA_DELAY_INTERVAL, required = false) String delayInMs,
                                                      ConsumerRecord<String, PaymentReconciliationMessage> consumerRecord) {
        try {
            scheduler.schedule(() -> processorPool.submit(() -> {
                try {
                    log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
                    consume(consumerRecord.value());
                } catch (Exception e) {
                    kafkaRetryHandlerService.retry(consumerRecord, lastAttemptedSequence, createdAt, lastProcessedAt, retryCount);
                    if (!(e instanceof WynkRuntimeException)) {
                        log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Something went wrong while processing message for PaymentReconciliationMessage kafka consumer : txnId - {} , error - {}", consumerRecord.value().getTransactionId(), e.getMessage(), e);
                    }
                }
            }), Optional.ofNullable(delayInMs).map(Integer::parseInt).orElse(0), TimeUnit.MILLISECONDS);
            AnalyticService.update("processorPoolSize", processorPool.getPoolSize());
            AnalyticService.update("processorActiveCount", processorPool.getActiveCount());
            AnalyticService.update("processorQueueSize", processorPool.getQueue().size());
        } catch (Exception e) {
            log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Error occurred in scheduling paymentReconciliation message {} due to unexpected error : {}", consumerRecord.value(), e.getMessage(), e);
        }
    }

    private interface InnerPaymentStatusDelegator {
        void reconcile(AbstractTransactionStatusRequest request);
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