package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.pubsub.extractor.IPubSubMessageExtractor;
import in.wynk.pubsub.poller.AbstractPubSubMessagePolling;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PreDebitNotificationGCPConsumer extends AbstractPubSubMessagePolling<PreDebitNotificationMessage> {

    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    private final PaymentGatewayManager manager;
    @Value("${payments.pooling.pubSub.preDebitNotification.enabled}")
    private boolean pollingEnabled;
    @Value("${payments.pooling.pubSub.preDebitNotification.consumer.delay}")
    private long poolingDelay;
    @Value("${payments.pooling.pubSub.preDebitNotification.consumer.delayTimeUnit}")
    private TimeUnit poolingDelayTimeUnit;

    public PreDebitNotificationGCPConsumer(String projectName, String topicName, String subscriptionName, ObjectMapper objectMapper, IPubSubMessageExtractor pubSubMessageExtractor, ExecutorService messageHandlerThreadPool, ScheduledExecutorService pollingThreadPool, PaymentGatewayManager manager) {
        super(projectName, topicName, subscriptionName, messageHandlerThreadPool, objectMapper, pubSubMessageExtractor);
        this.messageHandlerThreadPool = messageHandlerThreadPool;
        this.pollingThreadPool = pollingThreadPool;
        this.manager = manager;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "preDebitNotificationMessage")
    public void consume(PreDebitNotificationMessage message) {
        AnalyticService.update(message);
        manager.notify(message);
    }

    @Override
    public Class<PreDebitNotificationMessage> messageType() {
        return PreDebitNotificationMessage.class;
    }

    @Override
    public void start() {
        if (pollingEnabled) {
            log.info("Starting PreDebitNotificationConsumerPollingQueue...");
            pollingThreadPool.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    poolingDelay,
                    poolingDelayTimeUnit
            );
        }

    }

    @Override
    public void stop() {
        if (pollingEnabled) {
            log.info("Shutting down PreDebitNotificationGCPConsumer...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
            pubSubMessageExtractor.stop();
        }

    }
}
