package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.constant.StatusMode;
import in.wynk.payment.core.event.PaymentReconciledEvent;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.request.ChargingStatusRequest;
import in.wynk.payment.dto.response.ChargingStatusResponse;
import in.wynk.payment.service.PaymentManager;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentReconciliationConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PaymentReconciliationMessage> {

    @Value("${payment.pooling.queue.reconciliation.enabled}")
    private boolean reconciliationPollingEnabled;
    @Value("${payment.pooling.queue.reconciliation.sqs.consumer.delay}")
    private long reconciliationPoolingDelay;
    @Value("${payment.pooling.queue.reconciliation.sqs.consumer.delayTimeUnit}")
    private TimeUnit reconciliationPoolingDelayTimeUnit;

    private final ApplicationEventPublisher eventPublisher;
    private final ThreadPoolExecutor messageHandlerThreadPool;
    private final ScheduledThreadPoolExecutor pollingThreadPool;

    @Autowired
    private PaymentManager paymentManager;

    public PaymentReconciliationConsumerPollingQueue(String queueName,
                                                     AmazonSQS sqs,
                                                     ISQSMessageExtractor messagesExtractor,
                                                     ApplicationEventPublisher eventPublisher,
                                                     ThreadPoolExecutor messageHandlerThreadPool,
                                                     ScheduledThreadPoolExecutor pollingThreadPool) {
        super(queueName, sqs, messagesExtractor, messageHandlerThreadPool);
        this.eventPublisher = eventPublisher;
        this.pollingThreadPool = pollingThreadPool;
        this.messageHandlerThreadPool = messageHandlerThreadPool;
    }

    @Override
    @AnalyseTransaction(name = "paymentReconciliation")
    public void consume(PaymentReconciliationMessage message) {
        AnalyticService.update(message);
        log.info(PaymentLoggingMarker.PAYMENT_RECONCILIATION_QUEUE, "processing PaymentReconciliationMessage for uid {} and transactionId {}", message.getUid(), message.getTransactionId());
        ChargingStatusResponse response = (ChargingStatusResponse) paymentManager.status(ChargingStatusRequest.builder().transactionId(message.getTransactionId()).mode(StatusMode.SOURCE).build(), message.getPaymentCode(), false).getBody();
        eventPublisher.publishEvent(PaymentReconciledEvent.builder()
                .uid(message.getUid())
                .msisdn(message.getMsisdn())
                .itemId(message.getItemId())
                .planId(message.getPlanId())
                .clientId(message.getClientId())
                .transactionId(response.getTid())
                .paymentCode(message.getPaymentCode())
                .transactionEvent(message.getTransactionEvent())
                .transactionStatus(response.getTransactionStatus())
                .build());
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
}
