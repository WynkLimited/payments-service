package in.wynk.payment.config;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.consumer.*;
import in.wynk.payment.extractor.*;
import in.wynk.payment.service.IRecurringPaymentManagerService;
import in.wynk.payment.service.ITransactionManagerService;
import in.wynk.payment.service.PaymentManager;
import in.wynk.queue.constant.BeanConstant;
import in.wynk.queue.service.ISqsManagerService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

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
                threadPoolExecutor(),
                scheduledThreadPoolExecutor());
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
                threadPoolExecutor(),
                scheduledThreadPoolExecutor(),
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
                paymentManager, threadPoolExecutor(),
                scheduledThreadPoolExecutor());
    }

    @Bean
    public PaymentRecurringSchedulingPollingQueue paymentRecurringSchedulingPollingQueue(@Value("${payment.pooling.queue.schedule.name}") String queueName,
                                                                                         @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                         ObjectMapper objectMapper,
                                                                                         PaymentRecurringSchedulingSQSMessageExtractor paymentRecurringSchedulingSQSMessageExtractor,
                                                                                         PaymentManager paymentManager) {
        return new PaymentRecurringSchedulingPollingQueue(queueName,
                sqsClient,
                objectMapper,
                paymentRecurringSchedulingSQSMessageExtractor,
                paymentManager,
                threadPoolExecutor(),
                scheduledThreadPoolExecutor());
    }

    @Bean
    public PaymentRecurringUnSchedulingPollingQueue paymentRecurringUnSchedulingPollingQueue(@Value("${payment.pooling.queue.unschedule.name}") String queueName,
                                                                                             @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                             ObjectMapper objectMapper,
                                                                                             PaymentRecurringUnSchedulingSQSMessageExtractor paymentRecurringUnSchedulingSQSMessageExtractor,
                                                                                             @Qualifier(in.wynk.payment.core.constant.BeanConstant.RECURRING_PAYMENT_RENEWAL_SERVICE) IRecurringPaymentManagerService recurringPaymentManager) {
        return new PaymentRecurringUnSchedulingPollingQueue(queueName,
                sqsClient,
                objectMapper,
                paymentRecurringUnSchedulingSQSMessageExtractor,
                threadPoolExecutor(),
                scheduledThreadPoolExecutor(),
                recurringPaymentManager);
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

    @Bean
    public PaymentRecurringSchedulingSQSMessageExtractor paymentRecurringSchedulingSQSMessageExtractor(@Value("${payment.pooling.queue.schedule.name}") String queueName,
                                                                                                       @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClients) {
        return new PaymentRecurringSchedulingSQSMessageExtractor(queueName, sqsClients);
    }

    @Bean
    public PaymentRecurringUnSchedulingSQSMessageExtractor paymentRecurringUnSchedulingSQSMessageExtractor(@Value("${payment.pooling.queue.unschedule.name}") String queueName,
                                                                                                           @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClients) {
        return new PaymentRecurringUnSchedulingSQSMessageExtractor(queueName, sqsClients);
    }

    private ExecutorService threadPoolExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    private ScheduledExecutorService scheduledThreadPoolExecutor() {
        return Executors.newScheduledThreadPool(2);
    }

}
