package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.constant.BaseConstants;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.dto.gpbs.acknowledge.queue.PurchaseAcknowledgeMessageManager;
import in.wynk.payment.dto.gpbs.acknowledge.request.AbstractPaymentAcknowledgementRequest;
import in.wynk.payment.dto.gpbs.acknowledge.request.GooglePlayProductAcknowledgementRequest;
import in.wynk.payment.dto.gpbs.acknowledge.request.GooglePlaySubscriptionAcknowledgementRequest;
import in.wynk.payment.dto.gpbs.request.GooglePlayAppDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayPaymentDetails;
import in.wynk.payment.dto.gpbs.request.GooglePlayProductDetails;
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
public class PurchaseAcknowledgementConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PurchaseAcknowledgeMessageManager> {


    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    @Value("${payment.pooling.queue.acknowledgement.enabled}")
    private boolean subscriptionAcknowledgementPollingEnabled;
    @Value("${payment.pooling.queue.acknowledgement.sqs.consumer.delay}")
    private long subscriptionAcknowledgementPoolingDelay;
    @Value("${payment.pooling.queue.acknowledgement.sqs.consumer.delayTimeUnit}")
    private TimeUnit subscriptionAcknowledgementPoolingDelayTimeUnit;

    @Autowired
    private PaymentManager paymentManager;

    public PurchaseAcknowledgementConsumerPollingQueue (String queueName,
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
    @AnalyseTransaction(name = "purchaseAcknowledgeMessage")
    public void consume (PurchaseAcknowledgeMessageManager message) {
        AnalyticService.update(message);
        AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest = null;
        if (BeanConstant.GOOGLE_PLAY.equals(message.getPaymentCode())) {
            GooglePlayAppDetails appDetails = new GooglePlayAppDetails();
            appDetails.setPackageName(message.getPackageName());
            appDetails.setService(message.getService());
            GooglePlayProductDetails productDetails = new GooglePlayProductDetails();
            productDetails.setSkuId(message.getSkuId());
            if (BaseConstants.PLAN.equals(message.getType())) {
                abstractPaymentAcknowledgementRequest = GooglePlaySubscriptionAcknowledgementRequest.builder()
                        .paymentDetails(GooglePlayPaymentDetails.builder().purchaseToken(message.getPurchaseToken()).build())
                        .paymentCode(message.getPaymentCode())
                        .appDetails(appDetails)
                        .productDetails(productDetails)
                        .developerPayload(message.getDeveloperPayload())
                        .txnId(message.getTxnId())
                        .build();
            } else if (BaseConstants.POINT.equals(message.getType())) {
                abstractPaymentAcknowledgementRequest = GooglePlayProductAcknowledgementRequest.builder()
                        .paymentDetails(GooglePlayPaymentDetails.builder().purchaseToken(message.getPurchaseToken()).build())
                        .paymentCode(message.getPaymentCode())
                        .appDetails(appDetails)
                        .productDetails(productDetails)
                        .developerPayload(message.getDeveloperPayload())
                        .txnId(message.getTxnId())
                        .build();
            }
        }
        assert abstractPaymentAcknowledgementRequest != null;
        paymentManager.acknowledgeSubscription(abstractPaymentAcknowledgementRequest);
    }

    @Override
    public Class<PurchaseAcknowledgeMessageManager> messageType () {
        return PurchaseAcknowledgeMessageManager.class;
    }

    @Override
    public void start () {
        if (subscriptionAcknowledgementPollingEnabled) {
            log.info("Starting SubscriptionAcknowledgementConsumerPollingQueue...");
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
            log.info("Shutting down SubscriptionAcknowledgementConsumerPollingQueue ...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
        }

    }
}
