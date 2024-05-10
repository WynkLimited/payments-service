package in.wynk.payment.config;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.consumer.*;
import in.wynk.payment.extractor.ExternalTransactionPubSubMessageExtractor;
import in.wynk.payment.extractor.PaymentReconciliationGCPMessageExtractor;
import in.wynk.payment.extractor.PaymentReconciliationSQSMessageExtractor;
import in.wynk.payment.extractor.PurchaseAcknowledgementGCPMessageExtractor;
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

    @Bean
    public PurchaseAcknowledgementGCPConsumer purchaseAcknowledgementGCPConsumer(@Value("${payments.pooling.pubSub.acknowledgement.projectName}") String projectName,
                                                                                        @Value("${payments.pooling.pubSub.acknowledgement.topicName}") String topicName,
                                                                                        @Value("${payments.pooling.pubSub.acknowledgement.subscriptionName}") String subscriptionName,
                                                                                        @Value("${payments.pooling.pubSub.acknowledgement.bufferInterval}") String bufferInterval,
                                                                                        ObjectMapper objectMapper) {
        return new PurchaseAcknowledgementGCPConsumer(
                projectName,topicName,subscriptionName,
                objectMapper,
                new PurchaseAcknowledgementGCPMessageExtractor(projectName,subscriptionName,bufferInterval),
                threadPoolExecutor(2),
                scheduledThreadPoolExecutor());
    }

    @Bean
    public PurchaseAcknowledgementGCPMessageExtractor purchaseAcknowledgementGCPMessageExtractor (@Value("${payments.pooling.pubSub.acknowledgement.projectName}") String projectName,
                                                                                              @Value("${payments.pooling.pubSub.acknowledgement.subscriptionName}") String subscriptionName,
                                                                                              @Value("${payments.pooling.pubSub.acknowledgement.bufferInterval}") String bufferInterval) {
        return new PurchaseAcknowledgementGCPMessageExtractor(projectName, subscriptionName,bufferInterval);
    }

    @Bean
    public ExternalTransactionReportGCPConsumer ExternalTransactionReportGCPConsumer(@Value("${payments.pooling.pubSub.externalTransaction.report.projectName}") String projectName,
                                                                                            @Value("${payments.pooling.pubSub.externalTransaction.report.topicName}") String topicName,
                                                                                            @Value("${payments.pooling.pubSub.externalTransaction.report.subscriptionName}") String subscriptionName,
                                                                                            @Value("${payments.pooling.pubSub.externalTransaction.report.bufferInterval}") String bufferInterval,
                                                                                            ObjectMapper objectMapper) {
        return new ExternalTransactionReportGCPConsumer(
                projectName,topicName,subscriptionName,
                objectMapper,
                new ExternalTransactionPubSubMessageExtractor(projectName,subscriptionName,bufferInterval),
                threadPoolExecutor(2),
                scheduledThreadPoolExecutor());
    }

    @Bean
    public ExternalTransactionPubSubMessageExtractor externalTransactionPubSubMessageExtractor (@Value("${payments.pooling.pubSub.externalTransaction.report.projectName}") String projectName,
                                                                                                @Value("${payments.pooling.pubSub.externalTransaction.report.subscriptionName}") String subscriptionName,
                                                                                                @Value("${payments.pooling.pubSub.externalTransaction.report.bufferInterval}") String bufferInterval) {
        return new ExternalTransactionPubSubMessageExtractor(projectName, subscriptionName,bufferInterval);
    }



    private ExecutorService threadPoolExecutor(int threadCount) {
        return Executors.newFixedThreadPool(threadCount);
    }

    private ScheduledExecutorService scheduledThreadPoolExecutor () {
        return Executors.newScheduledThreadPool(2);
    }

}
