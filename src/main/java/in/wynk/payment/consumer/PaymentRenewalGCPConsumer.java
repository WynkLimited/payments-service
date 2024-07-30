package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.PaymentRenewalMessage;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.RecurringTransactionUtils;
import in.wynk.pubsub.poller.AbstractPubSubMessagePolling;
import in.wynk.pubsub.service.IPubSubManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentRenewalGCPConsumer extends AbstractPubSubMessagePolling<PaymentRenewalMessage> {

    private final ObjectMapper objectMapper;
    private final IPubSubManagerService pubSubManagerService;
    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    private final ITransactionManagerService transactionManager;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;
    private RecurringTransactionUtils recurringTransactionUtils;
    private PaymentCachingService cachingService;
    @Value("${payments.pooling.pubSub.renewal.enabled}")
    private boolean renewalPollingEnabled;
    @Value("${payments.pooling.pubSub.renewal.consumer.delay}")
    private long renewalPoolingDelay;
    @Value("${payments.pooling.pubSub.renewal.consumer.delayTimeUnit}")
    private TimeUnit renewalPoolingDelayTimeUnit;

    public PaymentRenewalGCPConsumer(String projectName, String topicName, String subscriptionName, ObjectMapper objectMapper, IPubSubManagerService pubSubManagerService, ExecutorService messageHandlerThreadPool, ScheduledExecutorService pollingThreadPool, ITransactionManagerService transactionManager, ISubscriptionServiceManager subscriptionServiceManager, IRecurringPaymentManagerService recurringPaymentManagerService, PaymentCachingService cachingService, RecurringTransactionUtils recurringTransactionUtils) {
        super(projectName, topicName, subscriptionName, messageHandlerThreadPool , pollingThreadPool, objectMapper);
        this.objectMapper = objectMapper;
        this.pubSubManagerService = pubSubManagerService;
        this.messageHandlerThreadPool = messageHandlerThreadPool;
        this.pollingThreadPool = pollingThreadPool;
        this.transactionManager = transactionManager;
        this.subscriptionServiceManager = subscriptionServiceManager;
        this.recurringPaymentManagerService = recurringPaymentManagerService;
        this.cachingService= cachingService;
        this.recurringTransactionUtils = recurringTransactionUtils;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentRenewalMessage")
    public void consume(PaymentRenewalMessage message) {
        AnalyticService.update(message);
        log.info(PaymentLoggingMarker.PAYMENT_RENEWAL_QUEUE, "processing PaymentRenewalMessage for transactionId {}", message.getTransactionId());
        Transaction transaction = transactionManager.get(message.getTransactionId());
        if (recurringTransactionUtils.isEligibleForRenewal(transaction, false)) {
            pubSubManagerService.publishPubSubMessage(PaymentRenewalChargingMessage.builder()
                    .uid(transaction.getUid())
                    .id(transaction.getIdStr())
                    .planId(transaction.getPlanId())
                    .msisdn(transaction.getMsisdn())
                    .clientAlias(transaction.getClientAlias())
                    .attemptSequence(message.getAttemptSequence())
                    .paymentCode(transaction.getPaymentChannel().getId())
                    .build());
        }
    }

    @Override
    public Class<PaymentRenewalMessage> messageType() {
        return PaymentRenewalMessage.class;
    }

    @Override
    public void start() {
        if (renewalPollingEnabled) {
            log.info("Starting PaymentRenewalGCPConsumer...");
            /*pollingThreadPool.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    renewalPoolingDelay,
                    renewalPoolingDelayTimeUnit
            );*/
        }
    }

    @Override
    public void stop() {
        if (renewalPollingEnabled) {
            log.info("Shutting down PaymentRenewalGCPConsumer...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();

        }

    }
}
