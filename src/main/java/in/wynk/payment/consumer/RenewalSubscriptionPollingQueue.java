package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.common.messages.RenewalSubscriptionMessage;
import in.wynk.payment.core.constant.PaymentCode;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.request.TransactionInitRequest;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import java.util.Calendar;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class RenewalSubscriptionPollingQueue extends AbstractSQSMessageConsumerPollingQueue<RenewalSubscriptionMessage> {

    @Value("${payment.pooling.queue.recurring.enabled}")
    private boolean recurringPollingEnabled;
    @Value("${payment.pooling.queue.recurring.sqs.consumer.delay}")
    private long recurringPoolingDelay;
    @Value("${payment.pooling.queue.recurring.sqs.consumer.delayTimeUnit}")
    private TimeUnit recurringPoolingDelayTimeUnit;

    private final ThreadPoolExecutor threadPoolExecutor;
    private final ScheduledThreadPoolExecutor scheduledThreadPoolExecutor;
    @Autowired
    private PaymentCachingService paymentCachingService;
    @Autowired
    private ITransactionManagerService transactionManagerService;
    @Autowired
    private IRecurringPaymentManagerService recurringPaymentManagerService;

    public RenewalSubscriptionPollingQueue(String queueName, AmazonSQS sqs, ObjectMapper objectMapper, ISQSMessageExtractor messagesExtractor, ThreadPoolExecutor handlerThreadPool, ScheduledThreadPoolExecutor scheduledThreadPoolExecutor) {
        super(queueName, sqs, objectMapper, messagesExtractor, handlerThreadPool);
        this.threadPoolExecutor = handlerThreadPool;
        this.scheduledThreadPoolExecutor = scheduledThreadPoolExecutor;
    }

    @Override
    public void start() {
        if (recurringPollingEnabled) {
            log.info("Starting RenewalSubscriptionPollingQueue...");
            scheduledThreadPoolExecutor.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    recurringPoolingDelay,
                    recurringPoolingDelayTimeUnit
            );
        }
    }

    @Override
    public void stop() {
        if (recurringPollingEnabled) {
            log.info("Shutting down RenewalSubscriptionPollingQueue ...");
            scheduledThreadPoolExecutor.shutdownNow();
            threadPoolExecutor.shutdown();
        }
    }

    @Override
    @AnalyseTransaction(name = "RenewalSubscriptionMessage")
    public void consume(RenewalSubscriptionMessage message) {
        AnalyticService.update(message);
        log.info(PaymentLoggingMarker.RENEWAL_SUBSCRIPTION_QUEUE, "processing RenewalSubscriptionMessage {}", message);
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(message.getNextChargingDate());
        int planId = Integer.parseInt(message.getPlanId());
        double amount = paymentCachingService.getPlan(planId).getFinalPrice();
        PaymentCode paymentCode;
        if (message.getPaymentCode().equals("se"))
            paymentCode = PaymentCode.SE_BILLING;
        else
            paymentCode = PaymentCode.getFromCode(message.getPaymentCode());
        Transaction transaction = transactionManagerService.initiateTransaction(TransactionInitRequest.builder()
                .uid(message.getUid())
                .msisdn(message.getMsisdn())
                .clientAlias(message.getClientAlias())
                .planId(planId)
                .amount(amount)
                .paymentCode(paymentCode)
                .event(message.getEvent())
                .build());
        recurringPaymentManagerService.scheduleRecurringPayment(transaction.getIdStr(), calendar);
    }

    @Override
    public Class<RenewalSubscriptionMessage> messageType() {
        return RenewalSubscriptionMessage.class;
    }

}
