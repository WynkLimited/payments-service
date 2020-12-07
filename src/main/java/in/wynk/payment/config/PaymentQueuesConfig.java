package in.wynk.payment.config;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.consumer.PaymentReconciliationConsumerPollingQueue;
import in.wynk.payment.consumer.PaymentRenewalChargingConsumerPollingQueue;
import in.wynk.payment.consumer.PaymentRenewalConsumerPollingQueue;
import in.wynk.payment.extractor.PaymentReconciliationSQSMessageExtractor;
import in.wynk.payment.extractor.PaymentRenewalChargingSQSMessageExtractor;
import in.wynk.payment.extractor.PaymentRenewalSQSMessageExtractor;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentManager;
import in.wynk.queue.constant.BeanConstant;
import in.wynk.queue.service.ISqsManagerService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class PaymentQueuesConfig {

    @Bean
    public PaymentReconciliationConsumerPollingQueue paymentReconciliationConsumerPollingQueue(@Value("${payment.pooling.queue.reconciliation.name}") String queueName,
                                                                                               @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                               ObjectMapper objectMapper,
                                                                                               PaymentReconciliationSQSMessageExtractor paymentReconciliationSQSMessageExtractor) {
        return new PaymentReconciliationConsumerPollingQueue(queueName,
                sqsClient,
                objectMapper,
                paymentReconciliationSQSMessageExtractor,
                (ThreadPoolExecutor) threadPoolExecutor(),
                (ScheduledThreadPoolExecutor) scheduledThreadPoolExecutor());
    }

    @Bean
    public PaymentRenewalConsumerPollingQueue paymentRenewalConsumerPollingQueue(@Value("${payment.pooling.queue.renewal.name}") String queueName,
                                                                                 @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                 ObjectMapper objectMapper,
                                                                                 PaymentRenewalSQSMessageExtractor paymentRenewalSQSMessageExtractor,
                                                                                 ISqsManagerService sqsManagerService,
                                                                                 ITransactionManagerService transactionManager) {
        return new PaymentRenewalConsumerPollingQueue(queueName,
                sqsClient,
                objectMapper,
                paymentRenewalSQSMessageExtractor,
                (ThreadPoolExecutor) threadPoolExecutor(),
                (ScheduledThreadPoolExecutor) scheduledThreadPoolExecutor(),
                sqsManagerService,
                transactionManager);
    }

    @Bean
    public PaymentRenewalChargingConsumerPollingQueue paymentRenewalChargingConsumerPollingQueue(@Value("${payment.pooling.queue.charging.name}") String queueName,
                                                                                                 @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                                 ObjectMapper objectMapper,
                                                                                                 PaymentRenewalChargingSQSMessageExtractor paymentRenewalChargingSQSMessageExtractor,
                                                                                                 PaymentManager paymentManager) {
        return new PaymentRenewalChargingConsumerPollingQueue(queueName,
                sqsClient,
                objectMapper,
                paymentRenewalChargingSQSMessageExtractor,
                paymentManager, (ThreadPoolExecutor) threadPoolExecutor(),
                (ScheduledThreadPoolExecutor) scheduledThreadPoolExecutor());
    }

    @Bean
    public PaymentReconciliationSQSMessageExtractor paymentReconciliationSQSMessageExtractor(@Value("${payment.pooling.queue.reconciliation.name}") String queueName,
                                                                                             @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient) {
        return new PaymentReconciliationSQSMessageExtractor(queueName, sqsClient);
    }

    @Bean
    public PaymentRenewalSQSMessageExtractor paymentRenewalSQSMessageExtractor(@Value("${payment.pooling.queue.renewal.name}") String queueName,
                                                                                      @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient) {
        return new PaymentRenewalSQSMessageExtractor(queueName, sqsClient);
    }

    @Bean
    public PaymentRenewalChargingSQSMessageExtractor paymentRenewalChargingSQSMessageExtractor(@Value("${payment.pooling.queue.charging.name}") String queueName,
                                                                                       @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient) {
        return new PaymentRenewalChargingSQSMessageExtractor(queueName, sqsClient);
    }



    private ExecutorService threadPoolExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    private ScheduledExecutorService scheduledThreadPoolExecutor() {
        return Executors.newScheduledThreadPool(2);
    }

}
