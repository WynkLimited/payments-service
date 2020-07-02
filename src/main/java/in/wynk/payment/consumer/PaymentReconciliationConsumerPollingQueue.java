package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import in.wynk.commons.dto.SubscriptionNotificationMessage;
import in.wynk.commons.enums.FetchStrategy;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dto.ChargingStatus;
import in.wynk.payment.core.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.response.BaseResponse;
import in.wynk.payment.service.IMerchantPaymentStatusService;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import in.wynk.queue.producer.ISQSMessagePublisher;
import in.wynk.revenue.commons.TransactionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentReconciliationConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PaymentReconciliationMessage> {

    @Value("${payment.pooling.queue.reconciliation.enabled}")
    private boolean reconciliationPoolingEnabled;
    @Value("${payment.pooling.queue.subscription.name}")
    private String subscriptionQueue;
    @Value("${payment.pooling.queue.reconciliation.sqs.consumer.delay}")
    private long reconciliationPoolingDelay;
    @Value("${payment.pooling.queue.reconciliation.sqs.consumer.delayTimeUnit}")
    private TimeUnit reconciliationPoolingDelayTimeUnit;
    @Value("${payment.pooling.queue.subscription.sqs.producer.delayInSecond}")
    private int subscriptionMessageDelay;

    private final ApplicationContext applicationContext;
    private final ISQSMessagePublisher sqsMessagePublisher;
    private final ThreadPoolExecutor messageHandlerThreadPool;
    private final ScheduledThreadPoolExecutor pollingThreadPool;

    public PaymentReconciliationConsumerPollingQueue(String queueName,
                                                     AmazonSQS sqs,
                                                     ISQSMessageExtractor messagesExtractor,
                                                     ThreadPoolExecutor messageHandlerThreadPool,
                                                     ScheduledThreadPoolExecutor pollingThreadPool,
                                                     ISQSMessagePublisher sqsMessagePublisher,
                                                     ApplicationContext applicationContext) {
        super(queueName, sqs, messagesExtractor, messageHandlerThreadPool);
        this.pollingThreadPool = pollingThreadPool;
        this.messageHandlerThreadPool = messageHandlerThreadPool;
        this.applicationContext = applicationContext;
        this.sqsMessagePublisher = sqsMessagePublisher;
    }

    @Override
    public void consume(PaymentReconciliationMessage message) {
        log.info(PaymentLoggingMarker.PAYMENT_RECONCILIATION_QUEUE, "processing PaymentReconciliationMessage for uid {} and transactionId {}", message.getUid(), message.getTransactionId());

        IMerchantPaymentStatusService statusService = this.applicationContext.getBean(message.getPaymentCode().getCode(), IMerchantPaymentStatusService.class);
        BaseResponse<ChargingStatus> response = statusService.status(ChargingStatusRequest.builder()
                .transactionId(message.getTransactionId())
                .transactionEvent(message.getTransactionEvent())
                .fetchStrategy(FetchStrategy.DIRECT_SOURCE_EXTERNAL_WITHOUT_CACHE)
                .chargingTimestamp(message.getInitTimestamp())
                .packPeriod(message.getPackPeriod())
                .build());

        if (response.getBody().getTransactionStatus() == TransactionStatus.SUCCESS) {
            try {
                sqsMessagePublisher.publish(SendSQSMessageRequest.<SubscriptionNotificationMessage>builder()
                        .queueName(subscriptionQueue)
                        .delaySeconds(subscriptionMessageDelay)
                        .message(SubscriptionNotificationMessage.builder()
                                                                .uid(message.getUid())
                                                                .planId(message.getPlanId())
                                                                .transactionId(message.getTransactionId())
                                                                .transactionEvent(message.getTransactionEvent())
                                                                .transactionStatus(response.getBody().getTransactionStatus())
                                                                .build())
                        .build());
            } catch (Exception e) {
                throw new WynkRuntimeException(QueueErrorType.SQS001, e, "Failed to publish subscriptionNotificationMessage for uid: " + message.getUid() + " and transactionId: " + message.getTransactionId() + " due to " + e.getMessage());
            }
        }
    }

    @Override
    public Class<PaymentReconciliationMessage> messageType() {
        return PaymentReconciliationMessage.class;
    }

    @Override
    public void start() {
        if (reconciliationPoolingEnabled) {
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
        log.info("Shutting down PaymentReconciliationConsumerPollingQueue ...");
        pollingThreadPool.shutdownNow();
        messageHandlerThreadPool.shutdown();
    }
}
