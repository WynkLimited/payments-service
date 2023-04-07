package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.gateway.IPaymentStatus;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.service.PaymentManager;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentReconciliationConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PaymentReconciliationMessage> {

    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    @Value("${payment.pooling.queue.reconciliation.enabled}")
    private boolean reconciliationPollingEnabled;
    @Value("${payment.pooling.queue.reconciliation.sqs.consumer.delay}")
    private long reconciliationPoolingDelay;
    @Value("${payment.pooling.queue.reconciliation.sqs.consumer.delayTimeUnit}")
    private TimeUnit reconciliationPoolingDelayTimeUnit;

    @Autowired
    private PaymentCodeCachingService codeCache;

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
    @ClientAware(clientAlias = "#message.clientAlias")
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
        } else if (message.getPaymentEvent() == PaymentEvent.RENEW) {
            transactionStatusRequest = RenewalChargingTransactionReconciliationStatusRequest.builder()
                    .extTxnId(message.getExtTxnId())
                    .transactionId(message.getTransactionId())
                    .originalTransactionId(message.getOriginalTransactionId())
                    .originalAttemptSequence(message.getOriginalAttemptSequence())
                    .build();
        } else {
            transactionStatusRequest = ChargingTransactionReconciliationStatusRequest.builder()
                    .extTxnId(message.getExtTxnId())
                    .transactionId(message.getTransactionId())
                    .build();
        }

        final InnerPaymentStatusDelegator delegate = (AbstractTransactionStatusRequest) -> {
            final boolean canSupportRecon = BeanLocatorFactory.containsBeanOfType(codeCache.get(message.getPaymentCode()).getCode(), new ParameterizedTypeReference<IPaymentStatus<AbstractPaymentStatusResponse, AbstractTransactionStatusRequest>>() {
            });
            if (canSupportRecon) BeanLocatorFactory.getBean(PaymentGatewayManager.class).reconcile(transactionStatusRequest);
            else BeanLocatorFactory.getBean(PaymentManager.class).status(transactionStatusRequest);
        };
        delegate.reconcile(transactionStatusRequest);
    }

    /**
    * TODO: Remove once you move all the gateway over new interfaces
    */
    private interface InnerPaymentStatusDelegator {
        void reconcile(AbstractTransactionStatusRequest request);
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
