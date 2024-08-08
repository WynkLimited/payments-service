package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.gpbs.acknowledge.queue.ExternalTransactionReportMessageManager;
import in.wynk.payment.dto.gpbs.acknowledge.request.AbstractPaymentAcknowledgementRequest;
import in.wynk.payment.dto.gpbs.acknowledge.request.GooglePlayReportExternalTransactionRequest;
import in.wynk.payment.service.PaymentManager;
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
public class ExternalTransactionReportKafkaConsumer extends AbstractKafkaEventConsumer<String, ExternalTransactionReportMessageManager> {

    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    private final PaymentManager paymentManager;
    private final KafkaListenerEndpointRegistry endpointRegistry;
    @Autowired
    private KafkaRetryHandlerService<String, ExternalTransactionReportMessageManager> kafkaRetryHandlerService;

    public ExternalTransactionReportKafkaConsumer (PaymentManager paymentManager, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.paymentManager = paymentManager;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @TransactionAware(txnId = "#message.transactionId")
    @AnalyseTransaction(name = "externalTransactionReport")
    public void consume(ExternalTransactionReportMessageManager message) throws WynkRuntimeException {
        AnalyticService.update(message);
        Transaction transaction = TransactionContext.get();
        IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().orElse(null);
        AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest =
                GooglePlayReportExternalTransactionRequest.builder().transaction(transaction).externalTransactionToken(message.getExternalTransactionId()).paymentCode(BeanConstant.GOOGLE_PLAY)
                        .clientAlias(message.getClientAlias()).purchaseDetails(purchaseDetails).initialTransactionId(message.getInitialTransactionId()).build();
        paymentManager.acknowledgeSubscription(abstractPaymentAcknowledgementRequest);
    }

    @KafkaListener(id = "externalTransactionReportMessageManagerListener", topics = "${wynk.kafka.consumers.listenerFactory.externalTransactionReport[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.externalTransactionReport[0].name}")
    protected void listenExternalTransactionReportMessageManager(@Header(in.wynk.scheduler.queue.constant.BeanConstant.MESSAGE_LAST_ATTEMPTED_SEQUENCE) String lastAttemptedSequence,
                                                                 @Header(in.wynk.scheduler.queue.constant.BeanConstant.MESSAGE_CREATION_DATETIME) String createdAt,
                                                                 @Header(in.wynk.scheduler.queue.constant.BeanConstant.MESSAGE_LAST_PROCESSED_DATETIME) String lastProcessedAt,
                                                                 @Header(StreamConstant.RETRY_COUNT) String retryCount,
                                                                 ConsumerRecord<String, ExternalTransactionReportMessageManager> consumerRecord) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            kafkaRetryHandlerService.retry(consumerRecord, lastAttemptedSequence, createdAt, lastProcessedAt, retryCount);
            if (!(e instanceof WynkRuntimeException)) {
                log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Something went wrong while processing message {} for kafka consumer : {}", consumerRecord.value(), ", ExternalTransactionReportMessageManager - ", e);
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