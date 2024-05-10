package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.common.messages.PaymentRecurringUnSchedulingMessage;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.RecurringPaymentEvent;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.pubsub.extractor.IPubSubMessageExtractor;
import in.wynk.pubsub.poller.AbstractPubSubMessagePolling;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static in.wynk.payment.core.constant.PaymentConstants.ADD_TO_BILL;

@Slf4j
public class PaymentRecurringUnSchedulingGCPConsumer extends AbstractPubSubMessagePolling<PaymentRecurringUnSchedulingMessage> {

    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    private final IRecurringPaymentManagerService recurringPaymentManager;
    private final ITransactionManagerService transactionManagerService;
    private final ApplicationEventPublisher eventPublisher;
    @Value("${payments.pooling.pubSub.unschedule.enabled}")
    private boolean paymentRecurringUnSchedulePollingEnabled;
    @Value("${payments.pooling.pubSub.unschedule.consumer.delay}")
    private long paymentRecurringUnSchedulePollingDelay;
    @Value("${payments.pooling.pubSub.unschedule.consumer.delayTimeUnit}")
    private TimeUnit paymentRecurringUnSchedulePollingDelayTimeUnit;
    public PaymentRecurringUnSchedulingGCPConsumer(String projectName, String topicName, String subscriptionName, ObjectMapper objectMapper, IPubSubMessageExtractor pubSubMessageExtractor, ExecutorService messageHandlerThreadPool, ScheduledExecutorService pollingThreadPool, IRecurringPaymentManagerService recurringPaymentManager, ITransactionManagerService transactionManagerService, ApplicationEventPublisher eventPublisher) {
        super(projectName, topicName, subscriptionName, messageHandlerThreadPool, objectMapper, pubSubMessageExtractor);
        this.messageHandlerThreadPool = messageHandlerThreadPool;
        this.pollingThreadPool = pollingThreadPool;
        this.recurringPaymentManager = recurringPaymentManager;
        this.transactionManagerService = transactionManagerService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    public void consume(PaymentRecurringUnSchedulingMessage message) {
        log.info(PaymentLoggingMarker.PAYMENT_RECONCILIATION_QUEUE, "processing PaymentRecurringUnSchedulingMessage for uid {} and transactionId {}", message.getUid(), message.getTransactionId());
        final Transaction transaction = transactionManagerService.get(message.getTransactionId());
        if (Objects.nonNull(transaction) && transaction.getPaymentChannel().getId().equalsIgnoreCase(ADD_TO_BILL)) {
            eventPublisher.publishEvent(RecurringPaymentEvent.builder().transactionId(message.getTransactionId()).paymentEvent(message.getPaymentEvent()).build());
        } else {
            recurringPaymentManager.unScheduleRecurringPayment(message.getTransactionId(), message.getPaymentEvent(), message.getValidUntil(), message.getDeferredUntil());
        }
    }

    @Override
    public Class<PaymentRecurringUnSchedulingMessage> messageType() {
        return PaymentRecurringUnSchedulingMessage.class;
    }

    @Override
    public void start() {
        if (paymentRecurringUnSchedulePollingEnabled) {
            log.info("Starting PaymentRecurringUnSchedulingPollingQueue...");
            pollingThreadPool.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    paymentRecurringUnSchedulePollingDelay,
                    paymentRecurringUnSchedulePollingDelayTimeUnit
            );
        }

    }

    @Override
    public void stop() {
        if (paymentRecurringUnSchedulePollingEnabled) {
            log.info("Shutting down PaymentRecurringUnSchedulingPollingQueue ...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
            pubSubMessageExtractor.stop();
        }

    }
}
