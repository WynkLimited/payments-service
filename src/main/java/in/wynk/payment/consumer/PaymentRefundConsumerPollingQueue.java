package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.dto.PaymentRefundInitMessage;
import in.wynk.payment.dto.PaymentRefundInitRequest;
import in.wynk.payment.service.PaymentManager;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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

    public PaymentRefundConsumerPollingQueue(String queueName, AmazonSQS sqs, ObjectMapper objectMapper, ISQSMessageExtractor messagesExtractor, ExecutorService handlerThreadPool, ScheduledExecutorService scheduledThreadPoolExecutor, PaymentManager paymentManager) {
        super(queueName, sqs, objectMapper, messagesExtractor, handlerThreadPool);
        this.threadPoolExecutor = handlerThreadPool;
        this.scheduledThreadPoolExecutor = scheduledThreadPoolExecutor;
        this.paymentManager = paymentManager;
    }

    @Override
    public void start() {
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
    public void stop() {
        if (refundPollingEnabled) {
            log.info("Shutting down PaymentRefundInitMessage ...");
            scheduledThreadPoolExecutor.shutdownNow();
            threadPoolExecutor.shutdown();
        }
    }

    @Override
    @AnalyseTransaction(name = "paymentRefundInitMessage")
    public void consume(PaymentRefundInitMessage message) {
        log.info(PaymentLoggingMarker.PAYMENT_REFUND_QUEUE, "processing PaymentRefundInitMessage for txn id {}", message.getOriginalTransactionId());
        AnalyticService.update(message);
        WynkResponseEntity<?> response = paymentManager.refund(PaymentRefundInitRequest.builder()
                .originalTransactionId(message.getOriginalTransactionId())
                .reason(message.getReason())
                .build());
        AnalyticService.update(response.getBody());
    }

    @Override
    public Class<PaymentRefundInitMessage> messageType() {
        return PaymentRefundInitMessage.class;
    }
}
