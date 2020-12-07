package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.PaymentRenewalMessage;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import in.wynk.queue.service.ISqsManagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentRenewalConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PaymentRenewalMessage> {

    @Value("${payment.pooling.queue.renewal.enabled}")
    private boolean renewalPollingEnabled;
    @Value("${payment.pooling.queue.renewal.sqs.consumer.delay}")
    private long renewalPoolingDelay;
    @Value("${payment.pooling.queue.renewal.sqs.consumer.delayTimeUnit}")
    private TimeUnit renewalPoolingDelayTimeUnit;

    private final ObjectMapper objectMapper;
    private final ISqsManagerService sqsManagerService;
    private final ThreadPoolExecutor messageHandlerThreadPool;
    private final ScheduledThreadPoolExecutor pollingThreadPool;
    private final ITransactionManagerService transactionManager;

    public PaymentRenewalConsumerPollingQueue(String queueName,
                                              AmazonSQS sqs,
                                              ObjectMapper objectMapper,
                                              ISQSMessageExtractor messagesExtractor,
                                              ThreadPoolExecutor messageHandlerThreadPool,
                                              ScheduledThreadPoolExecutor pollingThreadPool,
                                              ISqsManagerService sqsManagerService,
                                              ITransactionManagerService transactionManager) {
        super(queueName, sqs, objectMapper, messagesExtractor, messageHandlerThreadPool);
        this.objectMapper = objectMapper;
        this.pollingThreadPool = pollingThreadPool;
        this.messageHandlerThreadPool = messageHandlerThreadPool;
        this.sqsManagerService = sqsManagerService;
        this.transactionManager = transactionManager;
    }

    @Override
    public void start() {
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
    @AnalyseTransaction(name = "paymentRenewalMessage")
    public void consume(PaymentRenewalMessage message) {
        AnalyticService.update(message);
        log.info(PaymentLoggingMarker.PAYMENT_RENEWAL_QUEUE, "processing PaymentRenewalMessage for transactionId {}", message.getTransactionId());
        Transaction transaction = transactionManager.get(message.getTransactionId());
        sqsManagerService.publishSQSMessage(PaymentRenewalChargingMessage.builder()
                .uid(transaction.getUid())
                .id(transaction.getIdStr())
                .planId(transaction.getPlanId())
                .msisdn(transaction.getMsisdn())
                .paymentCode(transaction.getPaymentChannel())
                .amount(String.valueOf(transaction.getAmount()))
                .build());
    }

    @Override
    public Class<PaymentRenewalMessage> messageType() {return PaymentRenewalMessage.class; }

}
