package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.PaymentRefundInitMessage;
import in.wynk.payment.dto.PaymentRefundInitRequest;
import in.wynk.payment.dto.aps.common.ApsConstant;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
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
import org.springframework.context.annotation.DependsOn;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

import java.util.EnumSet;

@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class PaymentRefundKafkaConsumer extends AbstractKafkaEventConsumer<String, PaymentRefundInitMessage> {

    private final PaymentManager paymentManager;
    private final PaymentGatewayManager paymentGatewayManager;
    private final ITransactionManagerService transactionManagerService;
    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    private final KafkaListenerEndpointRegistry endpointRegistry;
    @Autowired
    private KafkaRetryHandlerService<String, PaymentRefundInitMessage> kafkaRetryHandlerService;

    public PaymentRefundKafkaConsumer (PaymentManager paymentManager, PaymentGatewayManager paymentGatewayManager, ITransactionManagerService transactionManagerService, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.paymentManager = paymentManager;
        this.paymentGatewayManager = paymentGatewayManager;
        this.transactionManagerService = transactionManagerService;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    @AnalyseTransaction(name = "paymentRefundInitMessage")
    public void consume(PaymentRefundInitMessage message) throws WynkRuntimeException {
        log.info(PaymentLoggingMarker.PAYMENT_REFUND_QUEUE, "processing PaymentRefundInitMessage for txn id {}", message.getOriginalTransactionId());
        AnalyticService.update(message);
        Transaction transaction = transactionManagerService.get(message.getOriginalTransactionId());

        if (ApsConstant.AIRTEL_PAY_STACK.equalsIgnoreCase(transaction.getPaymentChannel().getCode())) {
            if (!EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION, PaymentEvent.MANDATE).contains(transaction.getType())) {
                AbstractPaymentRefundResponse response =
                        paymentGatewayManager.doRefund(PaymentRefundInitRequest.builder().originalTransactionId(message.getOriginalTransactionId()).reason(message.getReason()).build());
                AnalyticService.update(response);
            }
        } else {
            WynkResponseEntity<?> response = paymentManager.refund(PaymentRefundInitRequest.builder()
                    .originalTransactionId(message.getOriginalTransactionId())
                    .reason(message.getReason())
                    .build());
            AnalyticService.update(response.getBody());
        }
    }

    @KafkaListener(id = "paymentRefundInitMessageListener", topics = "${wynk.kafka.consumers.listenerFactory.paymentRefundMessage[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.paymentRefundMessage[0].name}")
    protected void listenPaymentRefundInitMessage(@Header(BeanConstant.MESSAGE_LAST_ATTEMPTED_SEQUENCE) String lastAttemptedSequence,
                                                  @Header(BeanConstant.MESSAGE_CREATION_DATETIME) String createdAt,
                                                  @Header(BeanConstant.MESSAGE_LAST_PROCESSED_DATETIME) String lastProcessedAt,
                                                  @Header(StreamConstant.RETRY_COUNT) String retryCount,
                                                  ConsumerRecord<String, PaymentRefundInitMessage> consumerRecord) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            kafkaRetryHandlerService.retry(consumerRecord, lastAttemptedSequence, createdAt, lastProcessedAt, retryCount);
            if (!(e instanceof WynkRuntimeException)) {
                log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Something went wrong while processing message {} for kafka consumer : {}", consumerRecord.value(), ", PaymentRefundInitMessage - ", e);
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