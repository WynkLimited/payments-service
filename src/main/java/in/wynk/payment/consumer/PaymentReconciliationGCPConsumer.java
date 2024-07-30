package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.core.event.PaymentRefundInitEvent;
import in.wynk.payment.core.service.PaymentCodeCachingService;
import in.wynk.payment.dto.PaymentReconciliationMessage;
import in.wynk.payment.dto.common.response.AbstractPaymentStatusResponse;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.gateway.IPaymentStatus;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.service.PaymentManager;
import in.wynk.pubsub.poller.AbstractPubSubMessagePolling;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.ParameterizedTypeReference;

import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentReconciliationGCPConsumer extends AbstractPubSubMessagePolling<PaymentReconciliationMessage> {

    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    private final ITransactionManagerService transactionManager;
    private final ApplicationEventPublisher eventPublisher;
    @Value("${payments.pooling.pubSub.reconciliation.enabled}")
    private boolean reconciliationPollingEnabled;
    @Value("${payments.pooling.pubSub.reconciliation.consumer.delay}")
    private long reconciliationPoolingDelay;
    @Value("${payments.pooling.pubSub.reconciliation.consumer.delayTimeUnit}")
    private TimeUnit reconciliationPoolingDelayTimeUnit;

    @Autowired
    private PaymentCodeCachingService codeCache;
    public PaymentReconciliationGCPConsumer(String projectName, String topicName, String subscriptionName, ObjectMapper objectMapper, ExecutorService messageHandlerThreadPool, ScheduledExecutorService pollingThreadPool, ITransactionManagerService transactionManager, ApplicationEventPublisher eventPublisher) {
        super(projectName, topicName, subscriptionName, messageHandlerThreadPool, pollingThreadPool, objectMapper);
        this.messageHandlerThreadPool = messageHandlerThreadPool;
        this.pollingThreadPool = pollingThreadPool;
        this.transactionManager = transactionManager;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "paymentReconciliation")
    public void consume(PaymentReconciliationMessage message) {
        Transaction transaction = transactionManager.get(message.getTransactionId());
        if (transaction.getStatus() == TransactionStatus.INPROGRESS) {
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
                final boolean canSupportRecon = BeanLocatorFactory.containsBeanOfType(codeCache.get(message.getPaymentCode()).getCode(), new ParameterizedTypeReference<IPaymentStatus<AbstractPaymentStatusResponse, in.wynk.payment.dto.request.AbstractTransactionStatusRequest>>() {
                });
                if (canSupportRecon) BeanLocatorFactory.getBean(PaymentGatewayManager.class).reconcile(transactionStatusRequest);
                else BeanLocatorFactory.getBean(PaymentManager.class).status(transactionStatusRequest);
            };
            delegate.reconcile(transactionStatusRequest);
        } else if (EnumSet.of(TransactionStatus.SUCCESS).contains(transaction.getStatus()) && transaction.getPaymentChannel().isTrialRefundSupported() && (EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION, PaymentEvent.MANDATE).contains(transaction.getType()))) {
            eventPublisher.publishEvent(PaymentRefundInitEvent.builder()
                    .reason("trial plan amount refund")
                    .originalTransactionId(transaction.getIdStr())
                    .build());
        }

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
            log.info("Starting PaymentReconciliationGCPConsumerPolling...");
            /*pollingThreadPool.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    reconciliationPoolingDelay,
                    reconciliationPoolingDelayTimeUnit
            );*/
        }
    }

    @Override
    public void stop() {
        if (reconciliationPollingEnabled) {
            log.info("Shutting down PaymentReconciliationGCPConsumerPolling ...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
        }
    }


}
