package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.dto.gpbs.queue.GoogleAcknowledgeMessageManager;
import in.wynk.payment.service.PaymentManager;
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
    @Value("${payment.pooling.queue.reconciliation.enabled}")
    private boolean reconciliationPollingEnabled;
    @Value("${payment.pooling.queue.reconciliation.sqs.consumer.delay}")
    private long reconciliationPoolingDelay;
    @Value("${payment.pooling.queue.reconciliation.sqs.consumer.delayTimeUnit}")
    private TimeUnit reconciliationPoolingDelayTimeUnit;
    @Autowired
    private PaymentManager paymentManager;

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

    }

    @Override
    public Class<GoogleAcknowledgeMessageManager> messageType () {
        return null;
    }

    @Override
    public void start () {

    }

    @Override
    public void stop () {

    }
}
