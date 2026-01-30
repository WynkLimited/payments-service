package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.constant.BaseConstants;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.gpbs.acknowledge.queue.PurchaseAcknowledgeMessageManager;
import in.wynk.payment.dto.gpbs.acknowledge.request.AbstractPaymentAcknowledgementRequest;
import in.wynk.payment.dto.gpbs.acknowledge.request.GooglePlayProductAcknowledgementRequest;
import in.wynk.payment.dto.gpbs.acknowledge.request.GooglePlaySubscriptionAcknowledgementRequest;
import in.wynk.payment.dto.gpbs.request.GooglePlayAppDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayPaymentDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayProductDetails;
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
public class PurchaseAcknowledgementKafkaConsumer extends AbstractKafkaEventConsumer<String, PurchaseAcknowledgeMessageManager> {

    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    private final PaymentManager paymentManager;
    private final KafkaListenerEndpointRegistry endpointRegistry;
    @Autowired
    private KafkaRetryHandlerService<String, PurchaseAcknowledgeMessageManager> kafkaRetryHandlerService;

    public PurchaseAcknowledgementKafkaConsumer (PaymentManager paymentManager, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.paymentManager = paymentManager;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "purchaseAcknowledgeMessage")
    public void consume(PurchaseAcknowledgeMessageManager message) throws WynkRuntimeException {
        AnalyticService.update(message);
        AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest = null;
        if (BeanConstant.GOOGLE_PLAY.equals(message.getPaymentCode())) {
            GooglePlayAppDetails appDetails = new GooglePlayAppDetails();
            appDetails.setPackageName(message.getPackageName());
            appDetails.setService(message.getService());
            GooglePlayProductDetails productDetails = new GooglePlayProductDetails();
            productDetails.setSkuId(message.getSkuId());
            if (BaseConstants.PLAN.equals(message.getType())) {
                abstractPaymentAcknowledgementRequest = GooglePlaySubscriptionAcknowledgementRequest.builder()
                        .paymentDetails(GooglePlayPaymentDetails.builder().purchaseToken(message.getPurchaseToken()).build())
                        .paymentCode(message.getPaymentCode())
                        .appDetails(appDetails)
                        .productDetails(productDetails)
                        .developerPayload(message.getDeveloperPayload())
                        .txnId(message.getTxnId())
                        .build();
            } else if (BaseConstants.POINT.equals(message.getType())) {
                abstractPaymentAcknowledgementRequest = GooglePlayProductAcknowledgementRequest.builder()
                        .paymentDetails(GooglePlayPaymentDetails.builder().purchaseToken(message.getPurchaseToken()).build())
                        .paymentCode(message.getPaymentCode())
                        .appDetails(appDetails)
                        .productDetails(productDetails)
                        .developerPayload(message.getDeveloperPayload())
                        .txnId(message.getTxnId())
                        .build();
            }
        }
        assert abstractPaymentAcknowledgementRequest != null;
        paymentManager.acknowledgeSubscription(abstractPaymentAcknowledgementRequest);
    }

    @KafkaListener(id = "purchaseAcknowledgeMessageManagerListener", topics = "${wynk.kafka.consumers.listenerFactory.purchaseAcknowledgement[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.purchaseAcknowledgement[0].name}")
    protected void listenPurchaseAcknowledgeMessageManager(@Header(value = StreamConstant.MESSAGE_LAST_ATTEMPTED_SEQUENCE, required = false) String lastAttemptedSequence,
                                                           @Header(value = StreamConstant.MESSAGE_CREATION_DATETIME, required = false) String createdAt,
                                                           @Header(value = StreamConstant.MESSAGE_LAST_PROCESSED_DATETIME, required = false) String lastProcessedAt,
                                                           @Header(value = StreamConstant.RETRY_COUNT, required = false) String retryCount,
                                                           ConsumerRecord<String, PurchaseAcknowledgeMessageManager> consumerRecord) {
        try {
            log.debug("Kafka consume record result {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            kafkaRetryHandlerService.retry(consumerRecord, lastAttemptedSequence, createdAt, lastProcessedAt, retryCount);
            if (!(e instanceof WynkRuntimeException)) {
                log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Something went wrong while processing message {} for kafka consumer : {}", consumerRecord.value(), ", PurchaseAcknowledgeMessageManager - ", e);
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