package in.wynk.payment.consumer;


import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.PaymentRenewalChargingMessageTest;
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
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;


@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class PaymentRenewalChargingKafkaConsumerTesting extends AbstractKafkaEventConsumer<String, PaymentRenewalChargingMessageTest> {

    private final PaymentManager paymentManager;
    private final PaymentGatewayManager manager;
    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;
    private final KafkaListenerEndpointRegistry endpointRegistry;
    @Autowired
    private KafkaRetryHandlerService<String, PaymentRenewalChargingMessageTest> kafkaRetryHandlerService;

    public PaymentRenewalChargingKafkaConsumerTesting(PaymentManager paymentManager, PaymentGatewayManager manager, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.paymentManager = paymentManager;
        this.manager = manager;
        this.endpointRegistry = endpointRegistry;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentRenewalChargingMessageLoadTesting")
    public void consume(PaymentRenewalChargingMessageTest message) throws WynkRuntimeException {
       try{
           log.info("message id {}:, msisdn :{}",message.getId(),message.getMsisdn());
           AnalyticService.update(message);
           AnalyticService.update("messageUID",message.getUid());
           AnalyticService.update("msisdn",message.getMsisdn());
           Thread.sleep(600);
       }catch (Exception exception){
           log.error("error in load testing consumer");
       }
    }

    @KafkaListener(id = "paymentRenewalChargingMessageLoadTestingListener", topics = "${wynk.kafka.consumers.listenerFactory.paymentRenewalChargingLoadTesting[0].factoryDetails.topic}", containerFactory = "${wynk.kafka.consumers.listenerFactory.paymentRenewalChargingLoadTesting[0].name}")
    protected void listenPaymentRenewalChargingMessageLoadTesting(@Header(value = StreamConstant.MESSAGE_LAST_ATTEMPTED_SEQUENCE, required = false) String lastAttemptedSequence,
                                                       @Header(value = StreamConstant.MESSAGE_CREATION_DATETIME, required = false) String createdAt,
                                                       @Header(value = StreamConstant.MESSAGE_LAST_PROCESSED_DATETIME, required = false) String lastProcessedAt,
                                                       @Header(value = StreamConstant.RETRY_COUNT, required = false) String retryCount,
                                                       ConsumerRecord<String, PaymentRenewalChargingMessageTest> consumerRecord, Acknowledgment acknowledgment) {
        try {
            log.info("Kafka consume record result for loadTesting {} for event {}", consumerRecord, consumerRecord.value().toString());
            consume(consumerRecord.value());
        } catch (Exception e) {
            kafkaRetryHandlerService.retry(consumerRecord, lastAttemptedSequence, createdAt, lastProcessedAt, retryCount);
            if (!(e instanceof WynkRuntimeException)) {
                log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Something went wrong while processing message {} for kafka consumer : {}", consumerRecord.value(), ", PaymentRenewalChargingMessageLoadTesting - ", e);
            }
        } finally {
            acknowledgment.acknowledge();
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
