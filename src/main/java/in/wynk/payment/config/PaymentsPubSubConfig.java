package in.wynk.payment.config;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.consumer.PaymentReconciliationConsumerPollingQueue;
import in.wynk.payment.consumer.PaymentReconciliationGCPConsumer;
import in.wynk.payment.extractor.PaymentReconciliationGCPMessageExtractor;
import in.wynk.payment.extractor.PaymentReconciliationSQSMessageExtractor;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.queue.constant.BeanConstant;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class PaymentsPubSubConfig {

    @Bean
    public PaymentReconciliationGCPConsumer paymentReconciliationGCPConsumer(@Value("${payments.pooling.pubSub.reconciliation.projectName}") String projectName,
                                                                             @Value("${payments.pooling.pubSub.reconciliation.topicName}") String topicName,
                                                                             @Value("${payments.pooling.pubSub.reconciliation.subscriptionName}") String subscriptionName,
                                                                             @Value("${payments.pooling.pubSub.reconciliation.bufferInterval}") String bufferInterval,
                                                                                      ObjectMapper objectMapper, ITransactionManagerService transactionManager, ApplicationEventPublisher eventPublisher) {
        return new PaymentReconciliationGCPConsumer(
                projectName,topicName,subscriptionName,
                objectMapper,
                new PaymentReconciliationGCPMessageExtractor(projectName,subscriptionName,bufferInterval),
                threadPoolExecutor(4),
                scheduledThreadPoolExecutor(), transactionManager, eventPublisher);
    }

    @Bean
    public PaymentReconciliationGCPMessageExtractor paymentReconciliationGCPMessageExtractor (@Value("${payments.pooling.pubSub.reconciliation.projectName}") String projectName,
                                                                                              @Value("${payments.pooling.pubSub.reconciliation.subscriptionName}") String subscriptionName,
                                                                                              @Value("${payments.pooling.pubSub.reconciliation.bufferInterval}") String bufferInterva) {
        return new PaymentReconciliationGCPMessageExtractor(projectName, subscriptionName,bufferInterva);
    }


    private ExecutorService threadPoolExecutor(int threadCount) {
        return Executors.newFixedThreadPool(threadCount);
    }

    private ScheduledExecutorService scheduledThreadPoolExecutor () {
        return Executors.newScheduledThreadPool(2);
    }

}
