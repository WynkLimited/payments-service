package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.annotation.analytic.core.annotations.AnalyseTransaction;
import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.request.PaymentRenewalChargingRequest;
import in.wynk.payment.service.PaymentManager;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;

import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentRenewalChargingConsumerPollingQueue extends AbstractSQSMessageConsumerPollingQueue<PaymentRenewalChargingMessage> {

    @Value("${payment.pooling.queue.charging.enabled}")
    private boolean chargingPollingEnabled;
    @Value("${payment.pooling.queue.charging.sqs.consumer.delay}")
    private long chargingPoolingDelay;
    @Value("${payment.pooling.queue.charging.sqs.consumer.delayTimeUnit}")
    private TimeUnit chargingPoolingDelayTimeUnit;

    private final PaymentManager paymentManager;
    private final ThreadPoolExecutor messageHandlerThreadPool;
    private final ScheduledThreadPoolExecutor pollingThreadPool;

    public PaymentRenewalChargingConsumerPollingQueue(String queueName,
                                                      AmazonSQS sqs,
                                                      ObjectMapper objectMapper,
                                                      ISQSMessageExtractor messagesExtractor,
                                                      PaymentManager paymentManager,
                                                      ThreadPoolExecutor messageHandlerThreadPool,
                                                      ScheduledThreadPoolExecutor pollingThreadPool) {
        super(queueName, sqs, objectMapper, messagesExtractor, messageHandlerThreadPool);
        this.paymentManager = paymentManager;
        this.pollingThreadPool = pollingThreadPool;
        this.messageHandlerThreadPool = messageHandlerThreadPool;
    }

    @Override
    public void start() {
        if (chargingPollingEnabled) {
            log.info("Starting PaymentChargingConsumerPollingQueue...");
            pollingThreadPool.scheduleWithFixedDelay(
                    this::poll,
                    0,
                    chargingPoolingDelay,
                    chargingPoolingDelayTimeUnit
            );
        }
    }

    @Override
    public void stop() {
        if (chargingPollingEnabled) {
            log.info("Shutting down PaymentChargingConsumerPollingQueue ...");
            pollingThreadPool.shutdownNow();
            messageHandlerThreadPool.shutdown();
        }
    }

    @Override
    @AnalyseTransaction(name = "paymentRenewalChargingMessage")
    public void consume(PaymentRenewalChargingMessage message) {
        AnalyticService.update(message);
        log.info(PaymentLoggingMarker.PAYMENT_CHARGING_QUEUE, "processing PaymentChargingMessage for transaction {}", message);
        paymentManager.doRenewal(PaymentRenewalChargingRequest.builder()
                .id(message.getId())
                .uid(message.getUid())
                .planId(message.getPlanId())
                .msisdn(message.getMsisdn())
                .amount(message.getAmount())
                .cardToken(message.getCardToken())
                .cardNumber(message.getCardNumber())
                .transactionId(message.getTransactionId())
                .merchantTransaction(message.getMerchantTransaction())
                .previousTransaction(message.getPreviousTransaction())
                .paidPartnerProductId(message.getPaidPartnerProductId())
                .externalTransactionId(message.getExternalTransactionId())
                .build(), message.getPaymentCode());
    }

    @Override
    public Class<PaymentRenewalChargingMessage> messageType() { return PaymentRenewalChargingMessage.class; }
}
