package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
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
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * @author Nishesh Pandey
 */
@Slf4j
public class ExternalTransactionReportConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<ExternalTransactionReportMessageManager> {
    private final ExecutorService messageHandlerThreadPool;
    private final ScheduledExecutorService pollingThreadPool;
    @Value("${payment.pooling.queue.externalTransaction.report.enabled}")
    private boolean externalTransactionReportPollingEnabled;
    @Value("${payment.pooling.queue.externalTransaction.report.sqs.consumer.delay}")
    private long externalTransactionReportPoolingDelay;
    @Value("${payment.pooling.queue.externalTransaction.report.sqs.consumer.delayTimeUnit}")
    private TimeUnit externalTransactionReportPoolingDelayTimeUnit;

    @Autowired
    private PaymentManager paymentManager;

    public ExternalTransactionReportConsumerPollingQueue (String queueName,
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
    @TransactionAware(txnId = "#message.transactionId")
    @AnalyseTransaction(name = "externalTransactionReport")
    public void consume (ExternalTransactionReportMessageManager message) {
        AnalyticService.update(message);
        Transaction transaction = TransactionContext.get();
        IPurchaseDetails purchaseDetails = TransactionContext.getPurchaseDetails().orElse(null);
        AbstractPaymentAcknowledgementRequest abstractPaymentAcknowledgementRequest =
                GooglePlayReportExternalTransactionRequest.builder().transaction(transaction).externalTransactionToken(message.getExternalTransactionId()).paymentCode(BeanConstant.GOOGLE_PLAY)
                        .clientAlias(message.getClientAlias()).purchaseDetails(purchaseDetails).initialTransactionId(message.getInitialTransactionId()).build();
        paymentManager.acknowledgeSubscription(abstractPaymentAcknowledgementRequest);
    }

    @Override
    public Class<ExternalTransactionReportMessageManager> messageType () {
        return ExternalTransactionReportMessageManager.class;
    }

    @Override
    public void start () {
        if (externalTransactionReportPollingEnabled) {
            log.info("Starting ExternalTransactionReportConsumerPollingQueue...");
            pollingThreadPool.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    externalTransactionReportPoolingDelay,
                    externalTransactionReportPoolingDelayTimeUnit
            );
        }

    }

    @Override
    public void stop () {
        if (externalTransactionReportPollingEnabled) {
            log.info("Shutting down ExternalTransactionReportConsumerPollingQueue...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
        }
    }
}
