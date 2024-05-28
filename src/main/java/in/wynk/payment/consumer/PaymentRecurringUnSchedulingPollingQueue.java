package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.common.messages.PaymentRecurringUnSchedulingMessage;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.RecurringPaymentEvent;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static in.wynk.payment.core.constant.PaymentConstants.ADD_TO_BILL;

@Slf4j
public class PaymentRecurringUnSchedulingPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PaymentRecurringUnSchedulingMessage> {

    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    private final IRecurringPaymentManagerService recurringPaymentManager;
    private final ITransactionManagerService transactionManagerService;
    private final ApplicationEventPublisher eventPublisher;
    @Value("${payment.pooling.queue.unschedule.enabled}")
    private boolean paymentRecurringUnSchedulePollingEnabled;
    @Value("${payment.pooling.queue.unschedule.sqs.consumer.delay}")
    private long paymentRecurringUnSchedulePollingDelay;
    @Value("${payment.pooling.queue.unschedule.sqs.consumer.delayTimeUnit}")
    private TimeUnit paymentRecurringUnSchedulePollingDelayTimeUnit;

    public PaymentRecurringUnSchedulingPollingQueue(String queueName,
                                                    AmazonSQS sqs,
                                                    ObjectMapper objectMapper,
                                                    ISQSMessageExtractor messagesExtractor,
                                                    ExecutorService messageHandlerThreadPool,
                                                    ScheduledExecutorService pollingThreadPool,
                                                    IRecurringPaymentManagerService recurringPaymentManager, ITransactionManagerService transactionManagerService, ApplicationEventPublisher eventPublisher) {
        super(queueName, sqs, objectMapper, messagesExtractor, messageHandlerThreadPool);
        this.pollingThreadPool = pollingThreadPool;
        this.recurringPaymentManager = recurringPaymentManager;
        this.messageHandlerThreadPool = messageHandlerThreadPool;
        this.transactionManagerService = transactionManagerService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentRecurringUnSchedulingMessage")
    public void consume(PaymentRecurringUnSchedulingMessage message) {
        log.info(PaymentLoggingMarker.PAYMENT_RECONCILIATION_QUEUE, "processing PaymentRecurringUnSchedulingMessage for uid {} and transactionId {}", message.getUid(), message.getTransactionId());
        // TODO : This is temporary solution. need to discuss with Zuber sir how we can remove this check of ADDTOBILL
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
        }
    }

}