package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.common.messages.PaymentUserDeactivationMessage;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.event.PaymentUserDeactivationEvent;
import in.wynk.pubsub.extractor.IPubSubMessageExtractor;
import in.wynk.pubsub.poller.AbstractPubSubMessagePolling;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentUserDeactivationGCPConsumer extends AbstractPubSubMessagePolling<PaymentUserDeactivationMessage> {

    @Value("${payments.pooling.pubSub.userDeactivation.enabled}")
    private boolean userDeactivationPollingEnabled;
    @Value("${payments.pooling.pubSub.userDeactivation.consumer.delay}")
    private long userDeactivationPoolingDelay;
    @Value("${payments.pooling.pubSub.userDeactivation.consumer.delayTimeUnit}")
    private TimeUnit userDeactivationPoolingDelayTimeUnit;

    private final ExecutorService threadPoolExecutor;
    private final ScheduledExecutorService scheduledThreadPoolExecutor;
    private final ApplicationEventPublisher eventPublisher;
    public PaymentUserDeactivationGCPConsumer(String projectName, String topicName, String subscriptionName, ObjectMapper objectMapper, IPubSubMessageExtractor pubSubMessageExtractor, ExecutorService threadPoolExecutor, ScheduledExecutorService scheduledThreadPoolExecutor, ApplicationEventPublisher eventPublisher) {
        super(projectName, topicName, subscriptionName, threadPoolExecutor, objectMapper, pubSubMessageExtractor);
        this.threadPoolExecutor = threadPoolExecutor;
        this.scheduledThreadPoolExecutor = scheduledThreadPoolExecutor;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void consume(PaymentUserDeactivationMessage message) {
        log.info(PaymentLoggingMarker.USER_DEACTIVATION_SUBSCRIPTION_QUEUE, "processing PaymentUserDeactivationMessage for uid {} and id {}", message.getUid(), message.getId());
        AnalyticService.update(message);
        //transactionManagerService.migrateOldTransactions(message.getId(), message.getUid(), message.getOldUid());
        eventPublisher.publishEvent(PaymentUserDeactivationEvent.builder().id(message.getId()).uid(message.getUid()).oldUid(message.getOldUid()).clientAlias(message.getClientAlias()).service(message.getService()).build());

    }

    @Override
    public Class<PaymentUserDeactivationMessage> messageType() {
        return PaymentUserDeactivationMessage.class;
    }

    @Override
    public void start() {
        if (userDeactivationPollingEnabled) {
            log.info("Starting PaymentUserDeactivationMessage...");
            scheduledThreadPoolExecutor.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    userDeactivationPoolingDelay,
                    userDeactivationPoolingDelayTimeUnit
            );
        }

    }

    @Override
    public void stop() {
        if (userDeactivationPollingEnabled) {
            log.info("Shutting down PaymentUserDeactivationMessage ...");
            scheduledThreadPoolExecutor.shutdownNow();
            threadPoolExecutor.shutdown();
            pubSubMessageExtractor.stop();
        }

    }
}
