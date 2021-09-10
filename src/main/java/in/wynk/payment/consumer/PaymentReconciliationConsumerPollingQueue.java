package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.event.PaymentsBranchEvent;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.request.AbstractTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.ChargingTransactionReconciliationStatusRequest;
import in.wynk.payment.dto.request.RefundTransactionReconciliationStatusRequest;
import in.wynk.payment.service.PaymentManager;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static in.wynk.tinylytics.constants.TinylyticsConstants.PAYMENT_RECONCILE_EVENT;

@Slf4j
public class PaymentReconciliationConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PaymentReconciliationMessage> {

    @Value("${payment.pooling.queue.reconciliation.enabled}")
    private boolean reconciliationPollingEnabled;
    @Value("${payment.pooling.queue.reconciliation.sqs.consumer.delay}")
    private long reconciliationPoolingDelay;
    @Value("${payment.pooling.queue.reconciliation.sqs.consumer.delayTimeUnit}")
    private TimeUnit reconciliationPoolingDelayTimeUnit;
    private ApplicationEventPublisher eventPublisher;

    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;

    @Autowired
    private PaymentManager paymentManager;

    public PaymentReconciliationConsumerPollingQueue(String queueName,
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
    @AnalyseTransaction(name = "paymentReconciliation")
    public void consume(PaymentReconciliationMessage message) {
        AnalyticService.update(message);
        log.info(PaymentLoggingMarker.PAYMENT_RECONCILIATION_QUEUE, "processing PaymentReconciliationMessage for uid {} and transactionId {}", message.getUid(), message.getTransactionId());
        final AbstractTransactionReconciliationStatusRequest transactionStatusRequest;
        if (message.getPaymentEvent() == PaymentEvent.REFUND) {
            transactionStatusRequest = RefundTransactionReconciliationStatusRequest.builder()
                    .extTxnId(message.getExtTxnId())
                    .transactionId(message.getTransactionId())
                    .build();
        } else {
            transactionStatusRequest = ChargingTransactionReconciliationStatusRequest.builder()
                    .extTxnId(message.getExtTxnId())
                    .transactionId(message.getTransactionId())
                    .build();
        }
        paymentManager.status(transactionStatusRequest);
        publishBranchEvent(PaymentsBranchEvent.<PaymentReconciliationMessage>builder().eventName(PAYMENT_RECONCILE_EVENT).data(message).build());
    }

    @Override
    public Class<PaymentReconciliationMessage> messageType() {
        return PaymentReconciliationMessage.class;
    }

    @Override
    public void start() {
        if (reconciliationPollingEnabled) {
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
        if (reconciliationPollingEnabled) {
            log.info("Shutting down PaymentReconciliationConsumerPollingQueue ...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
        }
    }

    private void publishBranchEvent(PaymentsBranchEvent paymentsBranchEvent) {
        eventPublisher.publishEvent(paymentsBranchEvent);
    }
}
