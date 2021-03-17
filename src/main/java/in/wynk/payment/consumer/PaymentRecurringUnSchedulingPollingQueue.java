package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.common.messages.PaymentRecurringUnSchedulingMessage;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentRecurringUnSchedulingPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PaymentRecurringUnSchedulingMessage> {

    @Value("${payment.pooling.queue.unschedule.enabled}")
    private boolean paymentRecurringUnSchedulePollingEnabled;
    @Value("${payment.pooling.queue.unschedule.sqs.consumer.delay}")
    private long paymentRecurringUnSchedulePollingDelay;
    @Value("${payment.pooling.queue.unschedule.sqs.consumer.delayTimeUnit}")
    private TimeUnit paymentRecurringUnSchedulePollingDelayTimeUnit;

    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    private final IRecurringPaymentManagerService recurringPaymentManager;

    public PaymentRecurringUnSchedulingPollingQueue(String queueName,
                                                    AmazonSQS sqs,
                                                    ObjectMapper objectMapper,
                                                    ISQSMessageExtractor messagesExtractor,
                                                    ExecutorService messageHandlerThreadPool,
                                                    ScheduledExecutorService pollingThreadPool,
                                                    IRecurringPaymentManagerService recurringPaymentManager) {
        super(queueName, sqs, objectMapper, messagesExtractor, messageHandlerThreadPool);
        this.pollingThreadPool = pollingThreadPool;
        this.recurringPaymentManager = recurringPaymentManager;
        this.messageHandlerThreadPool = messageHandlerThreadPool;
    }

    @Override
    public void consume(PaymentRecurringUnSchedulingMessage message) {
        log.info(PaymentLoggingMarker.PAYMENT_RECONCILIATION_QUEUE, "processing PaymentRecurringUnSchedulingMessage for uid {} and transactionId {}", message.getUid(), message.getTransactionId());
        recurringPaymentManager.unScheduleRecurringPayment(message.getTransactionId(), message.getPaymentEvent(), message.getValidUntil(), message.getDeferredUntil());
    }

    @Override
    public Class<PaymentRecurringUnSchedulingMessage> messageType() {
        return PaymentRecurringUnSchedulingMessage.class;
    }

    @Override
    public void start() {
        if (paymentRecurringUnSchedulePollingEnabled) {
            log.info("Starting PaymentRecurringUnSchedulingPollingQueue...");
            pollingThreadPool.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    paymentRecurringUnSchedulePollingDelay,
                    paymentRecurringUnSchedulePollingDelayTimeUnit
            );
        }
    }

    @Override
    public void stop() {
        if (paymentRecurringUnSchedulePollingEnabled) {
            log.info("Shutting down PaymentRecurringUnSchedulingPollingQueue ...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
        }
    }

}
