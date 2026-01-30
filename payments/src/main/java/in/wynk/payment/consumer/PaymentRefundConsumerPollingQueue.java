package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.Transaction;
import in.wynk.payment.dto.PaymentRefundInitMessage;
import in.wynk.payment.dto.PaymentRefundInitRequest;
import in.wynk.payment.dto.response.AbstractPaymentRefundResponse;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentGatewayManager;
import in.wynk.payment.service.PaymentManager;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import static in.wynk.payment.core.constant.BeanConstant.AIRTEL_PAY_STACK;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.EnumSet;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentRefundConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PaymentRefundInitMessage> {
    @Value("${payment.pooling.queue.refund.enabled}")
    private boolean refundPollingEnabled;
    @Value("${payment.pooling.queue.refund.sqs.consumer.delay}")
    private long refundPoolingDelay;
    @Value("${payment.pooling.queue.refund.sqs.consumer.delayTimeUnit}")
    private TimeUnit refundPoolingDelayTimeUnit;

    private final ExecutorService threadPoolExecutor;
    private final ScheduledExecutorService scheduledThreadPoolExecutor;
    private final PaymentManager paymentManager;
    private final PaymentGatewayManager paymentGatewayManager;
    private final ITransactionManagerService transactionManagerService;

    public PaymentRefundConsumerPollingQueue (String queueName, AmazonSQS sqs, ObjectMapper objectMapper, ISQSMessageExtractor messagesExtractor, ExecutorService handlerThreadPool,
                                              ScheduledExecutorService scheduledThreadPoolExecutor, PaymentManager paymentManager, ITransactionManagerService transactionManagerService,
                                              PaymentGatewayManager paymentGatewayManager) {
        super(queueName, sqs, objectMapper, messagesExtractor, handlerThreadPool);
        this.threadPoolExecutor = handlerThreadPool;
        this.scheduledThreadPoolExecutor = scheduledThreadPoolExecutor;
        this.paymentManager = paymentManager;
        this.transactionManagerService = transactionManagerService;
        this.paymentGatewayManager = paymentGatewayManager;
    }

    @Override
    public void start () {
        if (refundPollingEnabled) {
            log.info("Starting PaymentRefundInitMessage...");
            scheduledThreadPoolExecutor.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    refundPoolingDelay,
                    refundPoolingDelayTimeUnit
            );
        }
    }

    @Override
    public void stop () {
        if (refundPollingEnabled) {
            log.info("Shutting down PaymentRefundInitMessage ...");
            scheduledThreadPoolExecutor.shutdownNow();
            threadPoolExecutor.shutdown();
        }
    }

    @Override
    @AnalyseTransaction(name = "paymentRefundInitMessage")
    public void consume (PaymentRefundInitMessage message) {
        log.info(PaymentLoggingMarker.PAYMENT_REFUND_QUEUE, "processing PaymentRefundInitMessage for txn id {}", message.getOriginalTransactionId());
        AnalyticService.update(message);
        Transaction transaction = transactionManagerService.get(message.getOriginalTransactionId());

        if (AIRTEL_PAY_STACK.equalsIgnoreCase(transaction.getPaymentChannel().getCode())) {
            if (!EnumSet.of(PaymentEvent.TRIAL_SUBSCRIPTION, PaymentEvent.MANDATE).contains(transaction.getType())) {
                AbstractPaymentRefundResponse response =
                        paymentGatewayManager.doRefund(PaymentRefundInitRequest.builder().originalTransactionId(message.getOriginalTransactionId()).reason(message.getReason()).build());
                AnalyticService.update(response);
            }
        } else {
            WynkResponseEntity<?> response = paymentManager.refund(PaymentRefundInitRequest.builder()
                    .originalTransactionId(message.getOriginalTransactionId())
                    .reason(message.getReason())
                    .build());
            AnalyticService.update(response.getBody());
        }
    }

    @Override
    public Class<PaymentRefundInitMessage> messageType () {
        return PaymentRefundInitMessage.class;
    }
}
