package in.wynk.payment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.dto.PaymentReconciliationMessagePubSub;
import in.wynk.pubsub.extractor.IPubSubMessageExtractor;
import in.wynk.pubsub.poller.AbstractPubSubMessagePolling;
import org.springframework.beans.factory.annotation.Value;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Slf4j
public class PaymentReconciliationConsumerPollingPubSub extends AbstractPubSubMessagePolling<PaymentReconciliationMessagePubSub> {

    private final ScheduledExecutorService pollingThreadPool;
    public PaymentReconciliationConsumerPollingPubSub(@Value("wcf-starter-poc-sub") String subscriptionName, @Value("wcf-starter-poc") String topic, @Value("prj-wynk-stg-wcf-svc-01") String projectName, ExecutorService executorService, ObjectMapper objectMapper, IPubSubMessageExtractor pubSubMessageExtractor, ScheduledExecutorService pollingThreadPool) {
        super(subscriptionName, topic, projectName, executorService, objectMapper, pubSubMessageExtractor);

        this.pollingThreadPool = pollingThreadPool;
    }



    @Override
    public void consume(PaymentReconciliationMessagePubSub message) {
            log.info("message", message);
            return;
    }

    @Override
    public Class<PaymentReconciliationMessagePubSub> messageType() {
        return PaymentReconciliationMessagePubSub.class;
    }

    @Override
    public void start() {
        log.info("Starting PaymentReconciliationConsumerPollingPubsub...");
        pollingThreadPool.scheduleWithFixedDelay(
                this::poll,
                0,
                20,
                TimeUnit.SECONDS
        );
    }


    @Override
    public void stop() {
        log.info("Shutting down PaymentReconciliationConsumerPollingPubSub ...");
        pollingThreadPool.shutdownNow();
        executorService.shutdown();

    }
}
