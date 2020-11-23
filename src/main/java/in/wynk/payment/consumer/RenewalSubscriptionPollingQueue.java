package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.common.messages.RenewalSubscriptionMessage;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RenewalSubscriptionPollingQueue extends AbstractSQSMessageConsumerPollingQueue<RenewalSubscriptionMessage> {

    @Value("${payment.pooling.queue.recurring.enabled}")
    private boolean recurringPollingEnabled;
    @Value("${payment.pooling.queue.recurring.sqs.consumer.delay}")
    private long recurringPoolingDelay;
    @Value("${payment.pooling.queue.recurring.sqs.consumer.delayTimeUnit}")
    private TimeUnit recurringPoolingDelayTimeUnit;

    private final ThreadPoolExecutor threadPoolExecutor;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;

    public RenewalSubscriptionPollingQueue(String queueName, AmazonSQS sqs, ObjectMapper objectMapper, ISQSMessageExtractor messagesExtractor, ThreadPoolExecutor handlerThreadPool, ScheduledThreadPoolExecutor scheduledThreadPoolExecutor) {
        super(queueName, sqs, objectMapper, messagesExtractor, handlerThreadPool);
        this.threadPoolExecutor = handlerThreadPool;
        this.scheduledThreadPoolExecutor = scheduledThreadPoolExecutor;
    }

    @Override
    public void start() {
        if (recurringPollingEnabled) {
            log.info("Starting RenewalSubscriptionPollingQueue...");
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
            log.info("Shutting down RenewalSubscriptionPollingQueue ...");
            scheduledThreadPoolExecutor.shutdownNow();
            threadPoolExecutor.shutdown();
        }
    }

    @Override
    public void consume(RenewalSubscriptionMessage message) {
        log.info(PaymentLoggingMarker.RENEWAL_SUBSCRIPTION_QUEUE, "processing RenewalSubscriptionMessage {}", message);

    }

    @Override
    public Class<RenewalSubscriptionMessage> messageType() {
        return RenewalSubscriptionMessage.class;
    }
}
