package in.wynk.payment.consumer;

import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.common.constant.BaseConstants;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.logging.constants.LoggingConstants;
import in.wynk.payment.dto.aps.kafka.PayChargeReqMessage;
import in.wynk.payment.dto.aps.kafka.PaymentChargeRequestMessage;
import in.wynk.stream.constant.StreamMarker;
import in.wynk.stream.consumer.impl.AbstractKafkaEventConsumer;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.DependsOn;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Service;

/**
 * @author Nishesh Pandey
 */
@Slf4j
@Service
@DependsOn("kafkaConsumerConfig")
public class PaymentChargeConsumer extends AbstractKafkaEventConsumer<String, PaymentChargeRequestMessage> {

    private final PaymentChargeHandler paymentChargeHandler;

    @Value("${wynk.kafka.consumers.enabled}")
    private boolean enabled;

    private final KafkaListenerEndpointRegistry endpointRegistry;

    public PaymentChargeConsumer (PaymentChargeHandler paymentChargeHandler, KafkaListenerEndpointRegistry endpointRegistry) {
        super();
        this.paymentChargeHandler = paymentChargeHandler;
        this.endpointRegistry = endpointRegistry;
    }


    @KafkaListener(id = "paymentChargeListener", topics = "${wynk.kafka.consumers.listenerFactory.payment.charge[0].factoryDetails.topic}",
            containerFactory = "${wynk.kafka.consumers.listenerFactory.payment.charge[0].name}")
    @AnalyseTransaction(name = "paymentChargeConsumer")
    protected void listenPaymentCharge (@Header(BaseConstants.X_ORG_ID) String orgId,@Header(BaseConstants.X_SERVICE_ID) String serviceId,@Header(BaseConstants.X_SESSION_ID) String sessionId,@Header(BaseConstants.X_REQUEST_ID) String requestId, ConsumerRecord<String, PayChargeReqMessage> consumerRecord) {
        try {
            MDC.put(LoggingConstants.REQUEST_ID, requestId);
            PaymentChargeRequestMessage requestMessage = PaymentChargeRequestMessage.builder().message(consumerRecord.value())
                            .requestId(requestId).orgId(orgId).serviceId(serviceId).sessionId(sessionId).build();
            consume(requestMessage);
        } catch (Exception e) {
            log.error(StreamMarker.KAFKA_POLLING_CONSUMPTION_ERROR, "Error occurred in polling/consuming kafka event", e);
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
}
