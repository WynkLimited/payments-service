package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.dao.entity.PaymentRenewal;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PreDebitNotificationMessage;
import in.wynk.payment.dto.PreDebitRequest;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.service.impl.RecurringPaymentManager;
import in.wynk.pubsub.extractor.IPubSubMessageExtractor;
import in.wynk.pubsub.poller.AbstractPubSubMessagePolling;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.text.SimpleDateFormat;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import static in.wynk.common.enums.PaymentEvent.*;

@Slf4j
public class PreDebitNotificationGCPConsumer extends AbstractPubSubMessagePolling<PreDebitNotificationMessage> {

    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    private final PaymentGatewayManager manager;
    private final RecurringPaymentManager recurringPaymentManager;
    @Value("${payments.pooling.pubSub.preDebitNotification.enabled}")
    private boolean pollingEnabled;
    @Value("${payments.pooling.pubSub.preDebitNotification.consumer.delay}")
    private long poolingDelay;
    @Value("${payments.pooling.pubSub.preDebitNotification.consumer.delayTimeUnit}")
    private TimeUnit poolingDelayTimeUnit;

    public PreDebitNotificationGCPConsumer(String projectName, String topicName, String subscriptionName, ObjectMapper objectMapper, IPubSubMessageExtractor pubSubMessageExtractor, ExecutorService messageHandlerThreadPool, ScheduledExecutorService pollingThreadPool, PaymentGatewayManager manager, RecurringPaymentManager recurringPaymentManager) {
        super(projectName, topicName, subscriptionName, messageHandlerThreadPool, objectMapper, pubSubMessageExtractor);
        this.messageHandlerThreadPool = messageHandlerThreadPool;
        this.pollingThreadPool = pollingThreadPool;
        this.manager = manager;
        this.recurringPaymentManager = recurringPaymentManager;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @AnalyseTransaction(name = "preDebitNotificationMessage")
    @TransactionAware(txnId = "#message.transactionId")
    public void consume(PreDebitNotificationMessage message) {
        Transaction transaction = TransactionContext.get();
        PaymentRenewal paymentRenewal = recurringPaymentManager.getRenewalById(message.getTransactionId());
        if (Objects.nonNull(paymentRenewal) && (paymentRenewal.getTransactionEvent() == RENEW || paymentRenewal.getTransactionEvent() == SUBSCRIBE || paymentRenewal.getTransactionEvent() == DEFERRED)) {
            SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");
            PreDebitRequest request = PreDebitRequest.builder().planId(transaction.getPlanId()).transactionId(transaction.getIdStr()).renewalDay(format.format(paymentRenewal.getDay().getTime()))
                    .renewalHour(paymentRenewal.getHour())
                    .initialTransactionId(paymentRenewal.getInitialTransactionId()).lastSuccessTransactionId(paymentRenewal.getLastSuccessTransactionId()).uid(transaction.getUid())
                    .paymentCode(transaction.getPaymentChannel().getCode()).clientAlias(message.getClientAlias()).build();
            AnalyticService.update(request);
            manager.notify(request);
        }
    }

    @Override
    public Class<PreDebitNotificationMessage> messageType() {
        return PreDebitNotificationMessage.class;
    }

    @Override
    public void start() {
        if (pollingEnabled) {
            log.info("Starting PreDebitNotificationConsumerPollingQueue...");
            pollingThreadPool.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    poolingDelay,
                    poolingDelayTimeUnit
            );
        }

    }

    @Override
    public void stop() {
        if (pollingEnabled) {
            log.info("Shutting down PreDebitNotificationGCPConsumer...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
            pubSubMessageExtractor.stop();
        }

    }
}
