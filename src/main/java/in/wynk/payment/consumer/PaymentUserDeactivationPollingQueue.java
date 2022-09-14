package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.common.messages.PaymentUserDeactivationMessage;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.event.PaymentUserDeactivationEvent;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentManager;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentUserDeactivationPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PaymentUserDeactivationMessage> {
    @Value("${payment.pooling.queue.userDeactivation.enabled}")
    private boolean userDeactivationPollingEnabled;
    @Value("${payment.pooling.queue.userDeactivation.sqs.consumer.delay}")
    private long userDeactivationPoolingDelay;
    @Value("${payment.pooling.queue.userDeactivation.sqs.consumer.delayTimeUnit}")
    private TimeUnit userDeactivationPoolingDelayTimeUnit;

    private final ExecutorService threadPoolExecutor;
    private final ScheduledExecutorService scheduledThreadPoolExecutor;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentUserDeactivationPollingQueue(String queueName, AmazonSQS sqs, ObjectMapper objectMapper, ISQSMessageExtractor messagesExtractor, ExecutorService handlerThreadPool, ScheduledExecutorService scheduledThreadPoolExecutor, ApplicationEventPublisher eventPublisher) {
        super(queueName, sqs, objectMapper, messagesExtractor, handlerThreadPool);
        this.threadPoolExecutor = handlerThreadPool;
        this.scheduledThreadPoolExecutor = scheduledThreadPoolExecutor;
        this.eventPublisher = eventPublisher;
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
        }
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentUserDeactivationMessage")
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
}