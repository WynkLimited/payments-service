package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.client.aspect.advice.ClientAware;
import in.wynk.payment.aspect.advice.TransactionAware;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.dao.entity.IPurchaseDetails;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.TransactionContext;
import in.wynk.payment.dto.gpbs.acknowledge.queue.ExternalTransactionReportMessageManager;
import in.wynk.payment.dto.gpbs.acknowledge.request.AbstractPaymentAcknowledgementRequest;
import in.wynk.payment.dto.gpbs.acknowledge.request.GooglePlayReportExternalTransactionRequest;
import in.wynk.payment.service.PaymentManager;
import in.wynk.pubsub.poller.AbstractPubSubMessagePolling;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class ExternalTransactionReportGCPConsumer extends AbstractPubSubMessagePolling<ExternalTransactionReportMessageManager> {

    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    @Value("${payments.pooling.pubSub.externalTransaction.report.enabled}")
    private boolean externalTransactionReportPollingEnabled;
    @Value("${payments.pooling.pubSub.externalTransaction.report.consumer.delay}")
    private long externalTransactionReportPoolingDelay;
    @Value("${payments.pooling.pubSub.externalTransaction.report.consumer.delayTimeUnit}")
    private TimeUnit externalTransactionReportPoolingDelayTimeUnit;

    @Autowired
    private PaymentManager paymentManager;

    public ExternalTransactionReportGCPConsumer(String projectName, String topicName, String subscriptionName, ObjectMapper objectMapper, ExecutorService messageHandlerThreadPool, ScheduledExecutorService pollingThreadPool) {
        super(projectName, topicName, subscriptionName, messageHandlerThreadPool,pollingThreadPool, objectMapper);
        this.messageHandlerThreadPool = messageHandlerThreadPool;
        this.pollingThreadPool = pollingThreadPool;
    }

    @Override
    @ClientAware(clientAlias = "#message.clientAlias")
    @TransactionAware(txnId = "#message.transactionId")
    @AnalyseTransaction(name = "externalTransactionReport")
    public void consume(ExternalTransactionReportMessageManager message) {
        AnalyticService.update(message);
        Transaction transaction = TransactionContext.get();
        IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().orElse(null);
        AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest =
                GooglePlayReportExternalTransactionRequest.builder().transaction(transaction).externalTransactionToken(message.getExternalTransactionId()).paymentCode(BeanConstant.GOOGLE_PLAY)
                        .clientAlias(message.getClientAlias()).purchaseDetails(purchaseDetails).build();
        paymentManager.acknowledgeSubscription(abstractPaymentAcknowledgementRequest);

    }

    @Override
    public Class<ExternalTransactionReportMessageManager> messageType() {
        return ExternalTransactionReportMessageManager.class;
    }

    @Override
    public void start() {
        if (externalTransactionReportPollingEnabled) {
            log.info("Starting ExternalTransactionGCPReportConsumer...");
            /*pollingThreadPool.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    externalTransactionReportPoolingDelay,
                    externalTransactionReportPoolingDelayTimeUnit
            );*/
        }

    }

    @Override
    public void stop() {
        if (externalTransactionReportPollingEnabled) {
            log.info("Shutting down ExternalTransactionGCPReportConsumer...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
        }

    }
}
