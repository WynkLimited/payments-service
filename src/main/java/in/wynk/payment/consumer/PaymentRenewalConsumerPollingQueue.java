package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.dto.WynkResponse;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.PaymentRenewalMessage;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.subscription.common.dto.RenewalPlanEligibilityResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentRenewalConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PaymentRenewalMessage> {

    private final ObjectMapper objectMapper;
    private final ISqsManagerService sqsManagerService;
    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    private final ITransactionManagerService transactionManager;
    private final ISubscriptionServiceManager subscriptionServiceManager;
    private final IRecurringPaymentManagerService recurringPaymentManagerService;
    private PaymentCachingService cachingService;
    @Value("${payment.pooling.queue.renewal.enabled}")
    private boolean renewalPollingEnabled;
    @Value("${payment.pooling.queue.renewal.sqs.consumer.delay}")
    private long renewalPoolingDelay;
    @Value("${payment.pooling.queue.renewal.sqs.consumer.delayTimeUnit}")
    private TimeUnit renewalPoolingDelayTimeUnit;

    public PaymentRenewalConsumerPollingQueue (String queueName,
                                               AmazonSQS sqs,
                                               ObjectMapper objectMapper,
                                               ISQSMessageExtractor messagesExtractor,
                                               ExecutorService messageHandlerThreadPool,
                                               ScheduledExecutorService pollingThreadPool,
                                               ISqsManagerService sqsManagerService,
                                               ITransactionManagerService transactionManager, ISubscriptionServiceManager subscriptionServiceManager,
                                               IRecurringPaymentManagerService recurringPaymentManagerService, PaymentCachingService cachingService) {
        super(queueName, sqs, objectMapper, messagesExtractor, messageHandlerThreadPool);
        this.objectMapper = objectMapper;
        this.pollingThreadPool = pollingThreadPool;
        this.messageHandlerThreadPool = messageHandlerThreadPool;
        this.sqsManagerService = sqsManagerService;
        this.transactionManager = transactionManager;
        this.subscriptionServiceManager = subscriptionServiceManager;
        this.recurringPaymentManagerService = recurringPaymentManagerService;
        this.cachingService = cachingService;
    }

    @Override
    public void start () {
        if (renewalPollingEnabled) {
            log.info("Starting PaymentRenewalConsumerPollingQueue...");
            pollingThreadPool.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    renewalPoolingDelay,
                    renewalPoolingDelayTimeUnit
            );
        }
    }

    @Override
    public void stop() {
        if (renewalPollingEnabled) {
            log.info("Shutting down PaymentRenewalConsumerPollingQueue ...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
        }
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentRenewalMessage")
    public void consume(PaymentRenewalMessage message) {
        AnalyticService.update(message);
        log.info(PaymentLoggingMarker.PAYMENT_RENEWAL_QUEUE, "processing PaymentRenewalMessage for transactionId {}", message.getTransactionId());
        Transaction transaction = transactionManager.get(message.getTransactionId());
        if (isEligibleForRenewal(transaction, message.getAttemptSequence())) {
            sqsManagerService.publishSQSMessage(PaymentRenewalChargingMessage.builder()
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

    private boolean isEligibleForRenewal (Transaction transaction, int attemptSequence) {
        if (attemptSequence < PaymentConstants.MAXIMUM_RENEWAL_RETRY_ALLOWED) {
            ResponseEntity<WynkResponse.WynkResponseWrapper<RenewalPlanEligibilityResponse>> response =
                    subscriptionServiceManager.renewalPlanEligibilityResponse(transaction.getPlanId(), transaction.getUid());
            if (Objects.nonNull(response.getBody()) && Objects.nonNull(response.getBody().getData())) {
                RenewalPlanEligibilityResponse renewalPlanEligibilityResponse = response.getBody().getData();
                long today = System.currentTimeMillis();
                long furtherDefer = renewalPlanEligibilityResponse.getDeferredUntil() - today;
                if (subscriptionServiceManager.isDeferred(transaction.getPaymentChannel().getCode(), furtherDefer)) {
                    recurringPaymentManagerService.unScheduleRecurringPayment(transaction.getIdStr(), PaymentEvent.DEFERRED, today, furtherDefer);
                    return false;
                }
            }
            return true;
        } else {
            log.error("Need to break the chain in Payment Renewal as maximum attempts are already exceeded");
            return false;
        }
    }

    @Override
    public Class<PaymentRenewalMessage> messageType() {
        return PaymentRenewalMessage.class;
    }

}