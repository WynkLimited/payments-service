package in.wynk.payment.consumer;

import com.amazonaws.services.sqs.AmazonSQS;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.dto.PaymentRenewalChargingMessage;
import in.wynk.payment.dto.request.PaymentRenewalRequest;
import in.wynk.payment.service.IMerchantPaymentRenewalService;
import in.wynk.payment.utils.BeanLocatorFactory;
import in.wynk.queue.extractor.ISQSMessageExtractor;
import in.wynk.queue.poller.AbstractSQSMessageConsumerPollingQueue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
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


    private final ThreadPoolExecutor messageHandlerThreadPool;
    private final ScheduledThreadPoolExecutor pollingThreadPool;

    public PaymentRenewalChargingConsumerPollingQueue(String queueName,
                                              AmazonSQS sqs,
                                              ISQSMessageExtractor messagesExtractor,
                                              ThreadPoolExecutor messageHandlerThreadPool,
                                              ScheduledThreadPoolExecutor pollingThreadPool) {
        super(queueName, sqs, messagesExtractor, messageHandlerThreadPool);
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
    public void consume(PaymentRenewalChargingMessage message) {
        log.info(PaymentLoggingMarker.PAYMENT_CHARGING_QUEUE, "processing PaymentChargingMessage for transaction {}", message.getPaymentRenewalRequest());

        IMerchantPaymentRenewalService merchantPaymentRenewalService = BeanLocatorFactory.getBean(message.getPaymentCode().getCode(), IMerchantPaymentRenewalService.class);
        merchantPaymentRenewalService.doRenewal(message.getPaymentRenewalRequest());
    }

    @Override
    public Class<PaymentRenewalChargingMessage> messageType() { return PaymentRenewalChargingMessage.class; }
}
