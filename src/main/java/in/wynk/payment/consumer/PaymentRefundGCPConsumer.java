package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.common.dto.WynkResponseEntity;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.dto.PaymentRefundInitMessage;
import in.wynk.payment.dto.PaymentRefundInitRequest;
import in.wynk.payment.service.PaymentManager;
import in.wynk.pubsub.poller.AbstractPubSubMessagePolling;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentRefundGCPConsumer extends AbstractPubSubMessagePolling<PaymentRefundInitMessage> {

    @Value("${payments.pooling.pubSub.refund.enabled}")
    private boolean refundPollingEnabled;
    @Value("${payments.pooling.pubSub.refund.consumer.delay}")
    private long refundPoolingDelay;
    @Value("${payments.pooling.pubSub.refund.consumer.delayTimeUnit}")
    private TimeUnit refundPoolingDelayTimeUnit;

    private final ExecutorService threadPoolExecutor;
    private final ScheduledExecutorService scheduledThreadPoolExecutor;
    private final PaymentManager paymentManager;
    public PaymentRefundGCPConsumer(String projectName, String topicName, String subscriptionName, ObjectMapper objectMapper, ExecutorService threadPoolExecutor, ScheduledExecutorService scheduledThreadPoolExecutor, PaymentManager paymentManager) {
        super(projectName, topicName, subscriptionName, threadPoolExecutor, scheduledThreadPoolExecutor, objectMapper);
        this.threadPoolExecutor = threadPoolExecutor;
        this.scheduledThreadPoolExecutor = scheduledThreadPoolExecutor;
        this.paymentManager = paymentManager;
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
}
