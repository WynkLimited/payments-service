package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.dto.PreDebitNotificationMessage;
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
    public void consume(PreDebitNotificationMessage message) {
        AnalyticService.update(message);
        manager.notify(message);
    }

    @Override
    public Class<PreDebitNotificationMessage> messageType() {
        return PreDebitNotificationMessage.class;
    }

}