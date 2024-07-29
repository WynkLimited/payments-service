package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.service.PaymentManager;
import in.wynk.pubsub.poller.AbstractPubSubMessagePolling;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static in.wynk.payment.core.constant.BeanConstant.AIRTEL_PAY_STACK;
import static in.wynk.payment.core.constant.BeanConstant.PAYU_MERCHANT_PAYMENT_SERVICE;

@Slf4j
public class PaymentRenewalChargingGCPConsumer extends AbstractPubSubMessagePolling<PaymentRenewalChargingMessage> {

    private final PaymentManager paymentManager;
    private final PaymentGatewayManager manager;
    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;

    @Value("${payments.pooling.pubSub.charging.enabled}")
    private boolean chargingPollingEnabled;
    @Value("${payments.pooling.pubSub.charging.consumer.delay}")
    private long chargingPoolingDelay;
    @Value("${payments.pooling.pubSub.charging.consumer.delayTimeUnit}")
    private TimeUnit chargingPoolingDelayTimeUnit;

    public PaymentRenewalChargingGCPConsumer(String projectName, String topicName, String subscriptionName, ObjectMapper objectMapper, ExecutorService messageHandlerThreadPool, ScheduledExecutorService pollingThreadPool,PaymentManager paymentManager, PaymentGatewayManager manager) {
        super(projectName, topicName, subscriptionName, messageHandlerThreadPool, pollingThreadPool, objectMapper);
        this.paymentManager = paymentManager;
        this.manager = manager;
        this.messageHandlerThreadPool = messageHandlerThreadPool;
        this.pollingThreadPool = pollingThreadPool;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentRenewalChargingMessage")
    public void consume(PaymentRenewalChargingMessage message) {
        AnalyticService.update(message);
        log.info(PaymentLoggingMarker.PAYMENT_CHARGING_QUEUE, "processing PaymentChargingMessage for transaction {}", message);
        //TODO: move payu also to new version after testing and remove check
        if (AIRTEL_PAY_STACK.equalsIgnoreCase(message.getPaymentCode()) || PAYU_MERCHANT_PAYMENT_SERVICE.equalsIgnoreCase(message.getPaymentCode())) {
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

    @Override
    public Class<PaymentRenewalChargingMessage> messageType() {
        return PaymentRenewalChargingMessage.class;
    }

    @Override
    public void start() {
        if (chargingPollingEnabled) {
            log.info("Starting PaymentRenewalChargingGCPConsumer...");
            pollingThreadPool.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    chargingPoolingDelay,
                    chargingPoolingDelayTimeUnit
            );
        }

    }

    @Override
    public void stop() {
        if (chargingPollingEnabled) {
            log.info("Shutting down PaymentRenewalChargingGCPConsumer ...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
        }

    }
}
