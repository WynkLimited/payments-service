package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import in.wynk.payment.core.dto.PaymentReconciliationMessage;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentReconciliationConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PaymentReconciliationMessage> {

    @Value("${payment.pooling.queue.reconciliation.enabled}")
    private boolean enabled;
    @Value("${payment.pooling.queue.reconciliation.delay}")
    private long poolingDelay;
    @Value("${payment.pooling.queue.reconciliation.delayTimeUnit}")
    private TimeUnit poolingDelayTimeUnit;
    private final ThreadPoolExecutor messageHandlerThreadPool;
    private final ScheduledThreadPoolExecutor pollingThreadPool;

    public PaymentReconciliationConsumerPollingQueue(String queueName,
                                                        AmazonSQS sqs,
                                                        ISQSMessageExtractor messagesExtractor,
                                                        ThreadPoolExecutor messageHandlerThreadPool,
                                                        ScheduledThreadPoolExecutor pollingThreadPool) {
        super(queueName, sqs, messagesExtractor, messageHandlerThreadPool);
        this.pollingThreadPool = pollingThreadPool;
        this.messageHandlerThreadPool = messageHandlerThreadPool;
    }

    @Override
    public void consume(PaymentReconciliationMessage message) {
        log.info(message.toString());
    }

    @Override
    public Class<PaymentReconciliationMessage> messageType() {
        return PaymentReconciliationMessage.class;
    }

    @Override
    public void start() {
        if (enabled) {
            log.info("Starting PaymentReconciliationConsumerPollingQueue...");
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
        log.info("Shutting down PaymentReconciliationConsumerPollingQueue ...");
        pollingThreadPool.shutdownNow();
        messageHandlerThreadPool.shutdown();
    }
}
