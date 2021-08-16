package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.request.PreDebitNotificationRequest;
import in.wynk.payment.service.IPreDebitNotificationService;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PreDebitNotificationConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PreDebitNotificationMessage> {

    @Value("${payment.pooling.queue.preDebitNotification.enabled}")
    private boolean pollingEnabled;
    @Value("${payment.pooling.queue.preDebitNotification.sqs.consumer.delay}")
    private long poolingDelay;
    @Value("${payment.pooling.queue.preDebitNotification.sqs.consumer.delayTimeUnit}")
    private TimeUnit poolingDelayTimeUnit;

    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;

    public PreDebitNotificationConsumerPollingQueue(String queueName,
                                                    AmazonSQS sqs,
                                                    ObjectMapper objectMapper,
                                                    ISQSMessageExtractor messagesExtractor,
                                                    ExecutorService messageHandlerThreadPool,
                                                    ScheduledExecutorService pollingThreadPool) {
        super(queueName, sqs, objectMapper, messagesExtractor, messageHandlerThreadPool);
        this.pollingThreadPool = pollingThreadPool;
        this.messageHandlerThreadPool = messageHandlerThreadPool;
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
            log.info("Shutting down PreDebitNotificationConsumerPollingQueue ...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
        }
    }

    @Override
    @TransactionAware(txnId = "#message.getTransactionId()")
    @AnalyseTransaction(name = "preDebitNotificationMessage")
    public void consume(PreDebitNotificationMessage message) {
        AnalyticService.update(message);
        log.info(PaymentLoggingMarker.PRE_DEBIT_NOTIFICATION_QUEUE, "processing PreDebitNotificationMessage for transactionId {}", message.getTransactionId());
        Transaction transaction = TransactionContext.get();
        BeanLocatorFactory.getBean(transaction.getPaymentChannel().getCode(), IPreDebitNotificationService.class)
                .sendPreDebitNotification(PreDebitNotificationRequest.builder()
                        .transactionId(message.getTransactionId())
                        .planId(transaction.getPlanId())
                        .date(message.getDate())
                        .build());
    }

    @Override
    public Class<PreDebitNotificationMessage> messageType() { return PreDebitNotificationMessage.class; }

}