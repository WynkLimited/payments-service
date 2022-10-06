package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.dto.GoogleAcknowledgeMessageManager;
import in.wynk.payment.service.GooglePlayService;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Nishesh Pandey
 */

@Slf4j
public class GooglePlayPaymentAcknowledgementConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<GoogleAcknowledgeMessageManager> {


    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    @Value("${payment.pooling.queue.acknowledgement.enabled}")
    private boolean subscriptionAcknowledgementPollingEnabled;
    @Value("${payment.pooling.queue.acknowledgement.sqs.consumer.delay}")
    private long subscriptionAcknowledgementPoolingDelay;
    @Value("${payment.pooling.queue.acknowledgement.sqs.consumer.delayTimeUnit}")
    private TimeUnit subscriptionAcknowledgementPoolingDelayTimeUnit;
    @Autowired
    private GooglePlayService googlePlayService;

    public GooglePlayPaymentAcknowledgementConsumerPollingQueue(String queueName,
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
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "subscriptionAcknowledgement")
    public void consume (GoogleAcknowledgeMessageManager message) {
        AnalyticService.update(message);
        googlePlayService.acknowledgeSubscription(message.getPackageName(), message.getService(), message.getPurchaseToken(), message.getDeveloperPayload(), message.getSkuId(), true);
    }

    @Override
    public Class<GoogleAcknowledgeMessageManager> messageType () {
        return GoogleAcknowledgeMessageManager.class;
    }

    @Override
    public void start () {
        if (subscriptionAcknowledgementPollingEnabled) {
            log.info("Starting GooglePlayPaymentAcknowledgementConsumerPollingQueue...");
            pollingThreadPool.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    subscriptionAcknowledgementPoolingDelay,
                    subscriptionAcknowledgementPoolingDelayTimeUnit
            );
        }

    }

    @Override
    public void stop () {
        if (subscriptionAcknowledgementPollingEnabled) {
            log.info("Shutting down GooglePlayPaymentAcknowledgementConsumerPollingQueue ...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
        }

    }
}
