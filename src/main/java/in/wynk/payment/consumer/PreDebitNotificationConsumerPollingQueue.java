package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.dto.PreDebitRequest;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.aps.request.predebit.PreDebitNotificationRequest;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PreDebitNotificationConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PreDebitNotificationMessage> {

    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    private final PaymentGatewayManager manager;
    @Value("${payment.pooling.queue.preDebitNotification.enabled}")
    private boolean pollingEnabled;
    @Value("${payment.pooling.queue.preDebitNotification.sqs.consumer.delay}")
    private long poolingDelay;
    @Value("${payment.pooling.queue.preDebitNotification.sqs.consumer.delayTimeUnit}")
    private TimeUnit poolingDelayTimeUnit;

    public PreDebitNotificationConsumerPollingQueue (String queueName, AmazonSQS sqs, ObjectMapper objectMapper, ISQSMessageExtractor messagesExtractor, ExecutorService messageHandlerThreadPool,
                                                     ScheduledExecutorService pollingThreadPool, PaymentGatewayManager manager) {
        super(queueName, sqs, objectMapper, messagesExtractor, messageHandlerThreadPool);
        this.pollingThreadPool = pollingThreadPool;
        this.messageHandlerThreadPool = messageHandlerThreadPool;
        this.manager = manager;
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
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "preDebitNotificationMessage")
    @TransactionAware(txnId = "#message.transactionId")
    public void consume(PreDebitNotificationMessage message) {
        Transaction transaction = TransactionContext.get();
        PreDebitRequest request = PreDebitRequest.builder().planId(transaction.getPlanId()).transactionId(transaction.getIdStr()).day(message.getDay()).hour(message.getHour())
                .initialTransactionId(message.getInitialTransactionId()).lastSuccessTransactionId(message.getLastSuccessTransactionId()).uid(transaction.getUid())
                .paymentCode(transaction.getPaymentChannel().getCode()).build();
        AnalyticService.update(request);
        manager.notify(request);
    }

    @Override
    public Class<PreDebitNotificationMessage> messageType() {
        return PreDebitNotificationMessage.class;
    }

}