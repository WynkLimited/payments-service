package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.common.ApsConstant;
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
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class PaymentRenewalChargingKafkaConsumer extends AbstractKafkaEventConsumer<String, PaymentRenewalChargingMessage> {

    private final PaymentManager paymentManager;
    private final PaymentGatewayManager manager;
    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    private final KafkaListenerEndpointRegistry endpointRegistry;
    @Autowired
    private KafkaRetryHandlerService<String, PaymentRenewalChargingMessage> kafkaRetryHandlerService;

    public PaymentRenewalChargingKafkaConsumer (PaymentManager paymentManager, PaymentGatewayManager manager, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.paymentManager = paymentManager;
        this.manager = manager;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentRenewalChargingMessage")
    @TransactionAware(txnId = "#message.id")
    public void consume(PaymentRenewalChargingMessage message) {
        AnalyticService.update(message);
        log.info(PaymentLoggingMarker.PAYMENT_CHARGING_QUEUE, "processing PaymentChargingMessage for transaction {}", message);
        Transaction transaction = TransactionContext.get();
        //TODO: move payu also to new version after testing and remove check
        if(TransactionStatus.CANCELLED != transaction.getStatus()) {
            if (ApsConstant.AIRTEL_PAY_STACK.equalsIgnoreCase(message.getPaymentCode())) {
                manager.renew(PaymentRenewalChargingRequest.builder()
                        .id(message.getId())
                        .uid(message.getUid())
                        .planId(message.getPlanId())
                        .msisdn(message.getMsisdn())
                        .attemptSequence(message.getAttemptSequence())
                        .clientAlias(message.getClientAlias())
                        .paymentGateway(PaymentCodeCachingService.getFromPaymentCode(message.getPaymentCode()))
                        .build());
            } else {
                paymentManager.doRenewal(PaymentRenewalChargingRequest.builder()
                        .id(message.getId())
                        .uid(message.getUid())
                        .planId(message.getPlanId())
                        .msisdn(message.getMsisdn())
                        .attemptSequence(message.getAttemptSequence())
                        .clientAlias(message.getClientAlias())
                        .paymentGateway(PaymentCodeCachingService.getFromPaymentCode(message.getPaymentCode()))
                        .build());
            }
        }
    }

    @KafkaListener(id = "paymentRenewalChargingMessageListener", topics = "${wynk.kafka.consumers.listenerFactory.paymentRenewalCharging[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.paymentRenewalCharging[0].name}")
    protected void listenPaymentRenewalChargingMessage(@Header(value = StreamConstant.MESSAGE_LAST_ATTEMPTED_SEQUENCE, required = false) String lastAttemptedSequence,
                                                       @Header(value = StreamConstant.MESSAGE_CREATION_DATETIME, required = false) String createdAt,
                                                       @Header(value = StreamConstant.MESSAGE_LAST_PROCESSED_DATETIME, required = false) String lastProcessedAt,
                                                       @Header(value = StreamConstant.RETRY_COUNT, required = false) String retryCount,
                                                       ConsumerRecord<String, PaymentRenewalChargingMessage> consumerRecord) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            kafkaRetryHandlerService.retry(consumerRecord, lastAttemptedSequence, createdAt, lastProcessedAt, retryCount);
            if (!(e instanceof WynkRuntimeException)) {
                log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Something went wrong while processing message {} for kafka consumer : {}", consumerRecord.value(), ", PaymentRenewalChargingMessage - ", e);
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