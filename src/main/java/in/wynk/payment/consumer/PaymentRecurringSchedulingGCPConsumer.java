package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.common.messages.PaymentRecurringSchedulingMessage;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.dto.request.MigrationTransactionRequest;
import in.wynk.payment.service.PaymentManager;
import in.wynk.pubsub.extractor.IPubSubMessageExtractor;
import in.wynk.pubsub.poller.AbstractPubSubMessagePolling;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentRecurringSchedulingGCPConsumer extends AbstractPubSubMessagePolling<PaymentRecurringSchedulingMessage> {

    @Value("${payments.pooling.pubSub.schedule.enabled}")
    private boolean recurringPollingEnabled;
    @Value("${payments.pooling.pubSub.schedule.consumer.delay}")
    private long recurringPoolingDelay;
    @Value("${payments.pooling.pubSub.schedule.consumer.delayTimeUnit}")
    private TimeUnit recurringPoolingDelayTimeUnit;

    private final PaymentManager paymentManager;
    private final ExecutorService threadPoolExecutor;
    private final ScheduledExecutorService scheduledThreadPoolExecutor;

    public PaymentRecurringSchedulingGCPConsumer(String projectName, String topicName, String subscriptionName, ObjectMapper objectMapper, IPubSubMessageExtractor pubSubMessageExtractor, ExecutorService threadPoolExecutor, ScheduledExecutorService scheduledThreadPoolExecutor,PaymentManager paymentManager) {
        super(projectName, topicName, subscriptionName, threadPoolExecutor, objectMapper, pubSubMessageExtractor);
        this.paymentManager = paymentManager;
        this.threadPoolExecutor = threadPoolExecutor;
        this.scheduledThreadPoolExecutor = scheduledThreadPoolExecutor;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "renewalSubscriptionMessage")
    public void consume(PaymentRecurringSchedulingMessage message) {
        log.info(PaymentLoggingMarker.RENEWAL_SUBSCRIPTION_QUEUE, "processing PaymentRecurringSchedulingMessage {}", message);
        AnalyticService.update(message);
        paymentManager.addToPaymentRenewalMigration(MigrationTransactionRequest.from(message));
    }

    @Override
    public Class<PaymentRecurringSchedulingMessage> messageType() {
        return PaymentRecurringSchedulingMessage.class;
    }

    @Override
    public void start() {
        if (recurringPollingEnabled) {
            log.info("Starting PaymentRecurringSchedulingGCPConsumer...");
            scheduledThreadPoolExecutor.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    recurringPoolingDelay,
                    recurringPoolingDelayTimeUnit
            );
        }

    }

    @Override
    public void stop() {
        if (recurringPollingEnabled) {
            log.info("Shutting down PaymentRecurringSchedulingGCPConsumer ...");
            scheduledThreadPoolExecutor.shutdownNow();
            threadPoolExecutor.shutdown();
            pubSubMessageExtractor.stop();
        }

    }
}
