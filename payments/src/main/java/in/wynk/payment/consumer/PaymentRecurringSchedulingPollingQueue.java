package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.common.messages.PaymentRecurringSchedulingMessage;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.dto.request.MigrationTransactionRequest;
import in.wynk.payment.service.PaymentManager;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentRecurringSchedulingPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PaymentRecurringSchedulingMessage> {

    @Value("${payment.pooling.queue.schedule.enabled}")
    private boolean recurringPollingEnabled;
    @Value("${payment.pooling.queue.schedule.sqs.consumer.delay}")
    private long recurringPoolingDelay;
    @Value("${payment.pooling.queue.schedule.sqs.consumer.delayTimeUnit}")
    private TimeUnit recurringPoolingDelayTimeUnit;

    private final PaymentManager paymentManager;
    private final ExecutorService threadPoolExecutor;
    private final ScheduledExecutorService scheduledThreadPoolExecutor;

    public PaymentRecurringSchedulingPollingQueue(String queueName, AmazonSQS sqs, ObjectMapper objectMapper, ISQSMessageExtractor messagesExtractor, PaymentManager paymentManager, ExecutorService handlerThreadPool, ScheduledExecutorService scheduledThreadPoolExecutor) {
        super(queueName, sqs, objectMapper, messagesExtractor, handlerThreadPool);
        this.paymentManager = paymentManager;
        this.threadPoolExecutor = handlerThreadPool;
        this.scheduledThreadPoolExecutor = scheduledThreadPoolExecutor;
    }

    @Override
    public void start() {
        if (recurringPollingEnabled) {
            log.info("Starting PaymentRecurringSchedulingPollingQueue...");
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
            log.info("Shutting down PaymentRecurringSchedulingPollingQueue ...");
            scheduledThreadPoolExecutor.shutdownNow();
            threadPoolExecutor.shutdown();
        }
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

}
