package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.PaymentTDRDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.*;

import in.wynk.payment.service.impl.PaymentTDRManager;

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



@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class TdrProcessingKafkaConsumer extends AbstractKafkaEventConsumer<String, TdrProcessingMessage> {

    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    private final PaymentTDRManager manager;

    private final KafkaListenerEndpointRegistry endpointRegistry;
    @Autowired
    private KafkaRetryHandlerService<String, TdrProcessingMessage> kafkaRetryHandlerService;

    public TdrProcessingKafkaConsumer (PaymentTDRManager manager,KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.manager = manager;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "tdrProcessingMessage")
    @TransactionAware(txnId = "#message.transactionId")
    public void consume(TdrProcessingMessage message) throws WynkRuntimeException {

        PaymentTDRDetails details = message.getPaymentTDRDetails();
        PaymentTDRDetails paymentTDRDetails = PaymentTDRDetails.builder()
                .transactionId(details.getTransactionId())
                .planId(details.getPlanId())
                .uid(details.getUid())
                .referenceId(details.getReferenceId())
                .status(details.getStatus())
                .executionTime(details.getExecutionTime())
                .createdTimestamp(details.getCreatedTimestamp())
                .updatedTimestamp(details.getUpdatedTimestamp())
                .build();
        manager.processTransaction(paymentTDRDetails);

    }


    @KafkaListener(id = "tdrProcessingMessageListener", topics = "${wynk.kafka.consumers.listenerFactory.tdrProcessing[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.tdrProcessing[0].name}")
    protected void listenPreDebitNotificationMessageManager(@Header(value = StreamConstant.MESSAGE_LAST_ATTEMPTED_SEQUENCE, required = false) String lastAttemptedSequence,
                                                            @Header(value = StreamConstant.MESSAGE_CREATION_DATETIME, required = false) String createdAt,
                                                            @Header(value = StreamConstant.MESSAGE_LAST_PROCESSED_DATETIME, required = false) String lastProcessedAt,
                                                            @Header(value = StreamConstant.RETRY_COUNT) String retryCountHeader,
                                                            ConsumerRecord<String, TdrProcessingMessage> consumerRecord) {
        TdrProcessingMessage message = consumerRecord.value();
        int retryCount = 0;
        try {
            retryCount = retryCountHeader != null ? Integer.parseInt(retryCountHeader) : 0;
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            if (retryCount >= 3) {
                log.warn("Max retry count reached for transaction {}. Marking as FAILED.", message.getTransactionId());
                manager.markTransactionAsFailed(message.getPaymentTDRDetails());
                return;
            }
            consume(message);
        } catch (Exception e) {
            kafkaRetryHandlerService.retry(consumerRecord, lastAttemptedSequence, createdAt, lastProcessedAt, retryCountHeader);
            if (!(e instanceof WynkRuntimeException)) {
                log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Something went wrong while processing message {} for kafka consumer : {}", consumerRecord.value(), ", PreDebitNotificationMessageManager - ", e);
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
