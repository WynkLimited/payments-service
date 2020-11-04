package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.common.messages.UnSchedulePaymentRecurrenceMessage;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentRecurrenceUnSchedulingPollingQueue extends AbstractSQSMessageConsumerPollingQueue<UnSchedulePaymentRecurrenceMessage> {

    @Value("${payment.pooling.queue.recurring.enabled}")
    private boolean reconciliationPollingEnabled;
    @Value("${payment.pooling.queue.recurring.sqs.consumer.delay}")
    private long reconciliationPoolingDelay;
    @Value("${payment.pooling.queue.recurring.sqs.consumer.delayTimeUnit}")
    private TimeUnit reconciliationPoolingDelayTimeUnit;

    private final ThreadPoolExecutor messageHandlerThreadPool;
    private final ScheduledThreadPoolExecutor pollingThreadPool;
    private final IRecurringPaymentManagerService recurringPaymentManager;

    public PaymentRecurrenceUnSchedulingPollingQueue(String queueName,
                                                     AmazonSQS sqs,
                                                     ObjectMapper objectMapper,
                                                     ISQSMessageExtractor messagesExtractor,
                                                     ThreadPoolExecutor messageHandlerThreadPool,
                                                     ScheduledThreadPoolExecutor pollingThreadPool,
                                                     @Qualifier(BeanConstant.RECURRING_PAYMENT_RENEWAL_SERVICE) IRecurringPaymentManagerService recurringPaymentManager) {
        super(queueName, sqs, objectMapper, messagesExtractor, messageHandlerThreadPool);
        this.pollingThreadPool = pollingThreadPool;
        this.recurringPaymentManager = recurringPaymentManager;
        this.messageHandlerThreadPool = messageHandlerThreadPool;
    }

    @Override
    public void consume(UnSchedulePaymentRecurrenceMessage message) {
        log.info(PaymentLoggingMarker.PAYMENT_RECONCILIATION_QUEUE, "processing UnSchedulePaymentRecurrenceMessage for uid {} and transactionId {}", message.getUid(), message.getTransactionId());
        recurringPaymentManager.unScheduleRecurringPayment(message.getTransactionId());
    }

    @Override
    public Class<UnSchedulePaymentRecurrenceMessage> messageType() {
        return UnSchedulePaymentRecurrenceMessage.class;
    }

    @Override
    public void start() {
        if (reconciliationPollingEnabled) {
            log.info("Starting PaymentReconciliationConsumerPollingQueue...");
            pollingThreadPool.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    reconciliationPoolingDelay,
                    reconciliationPoolingDelayTimeUnit
            );
        }
    }

    @Override
    public void stop() {
        if (reconciliationPollingEnabled) {
            log.info("Shutting down PaymentReconciliationConsumerPollingQueue ...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
        }
    }
}
