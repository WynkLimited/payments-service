package in.wynk.payment.config;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.consumer.*;
import in.wynk.payment.extractor.*;
import in.wynk.payment.service.*;
import in.wynk.payment.service.impl.RecurringPaymentManager;
import in.wynk.payment.utils.RecurringTransactionUtils;
import in.wynk.queue.constant.BeanConstant;
import in.wynk.queue.service.ISqsManagerService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
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
                                                                                               PaymentReconciliationSQSMessageExtractor paymentReconciliationSQSMessageExtractor, ITransactionManagerService transactionManager, ApplicationEventPublisher eventPublisher) {
        return new PaymentReconciliationConsumerPollingQueue(queueName,
                sqsClient,
                objectMapper,
                paymentReconciliationSQSMessageExtractor,
                threadPoolExecutor(4),
                scheduledThreadPoolExecutor(), transactionManager, eventPublisher);
    }

    @Bean
    public PurchaseAcknowledgementConsumerPollingQueue purchaseAcknowledgementConsumerPollingQueue(@Value("${payment.pooling.queue.acknowledgement.name}") String queueName,
                                                                                                       @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                                       ObjectMapper objectMapper,
                                                                                                       PurchaseAcknowledgementSQSMessageExtractor purchaseAcknowledgementSQSMessageExtractor) {
        return new PurchaseAcknowledgementConsumerPollingQueue(queueName,
                sqsClient,
                objectMapper,
                purchaseAcknowledgementSQSMessageExtractor,
                threadPoolExecutor(2),
                scheduledThreadPoolExecutor());
    }

    @Bean
    public ExternalTransactionReportConsumerPollingQueue externalTransactionReportConsumerPollingQueue (@Value("${payment.pooling.queue.externalTransaction.report.name}") String queueName,
                                                                                                        @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                                        ObjectMapper objectMapper,
                                                                                                        ExternalTransactionSQSMessageExtractor externalTransactionSQSMessageExtractor) {
        return new ExternalTransactionReportConsumerPollingQueue(queueName,
                sqsClient,
                objectMapper,
                externalTransactionSQSMessageExtractor,
                threadPoolExecutor(2),
                scheduledThreadPoolExecutor());
    }

    @Bean
    public PaymentRenewalConsumerPollingQueue paymentRenewalConsumerPollingQueue (@Value("${payment.pooling.queue.renewal.name}") String queueName,
                                                                                  @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                  ObjectMapper objectMapper,
                                                                                  PaymentRenewalSQSMessageExtractor paymentRenewalSQSMessageExtractor,
                                                                                  ISqsManagerService sqsManagerService,
                                                                                  ITransactionManagerService transactionManager,
                                                                                  ISubscriptionServiceManager subscriptionServiceManager,
                                                                                  IRecurringPaymentManagerService recurringPaymentManagerService, PaymentCachingService cachingService, RecurringTransactionUtils recurringTransactionUtils) {
        return new PaymentRenewalConsumerPollingQueue(queueName,
                sqsClient,
                objectMapper,
                paymentRenewalSQSMessageExtractor,
                threadPoolExecutor(2),
                scheduledThreadPoolExecutor(),
                sqsManagerService,
                transactionManager,
                subscriptionServiceManager, recurringPaymentManagerService, cachingService, recurringTransactionUtils);
    }

    @Bean
    public PreDebitNotificationConsumerPollingQueue preDebitNotificationConsumerPollingQueue (@Value("${payment.pooling.queue.preDebitNotification.name}") String queueName,
                                                                                              @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                              ObjectMapper objectMapper,
                                                                                              PaymentGatewayManager manager, RecurringPaymentManager recurringPaymentManager,
                                                                                              PreDebitNotificationSQSMessageExtractor preDebitNotificationSQSMessageExtractor) {
        return new PreDebitNotificationConsumerPollingQueue(queueName,
                sqsClient,
                objectMapper,
                preDebitNotificationSQSMessageExtractor,
                threadPoolExecutor(2),
                scheduledThreadPoolExecutor(), manager, recurringPaymentManager);
    }

    @Bean
    public PaymentRenewalChargingConsumerPollingQueue paymentRenewalChargingConsumerPollingQueue (@Value("${payment.pooling.queue.charging.name}") String queueName,
                                                                                                  @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                                  ObjectMapper objectMapper,
                                                                                                  PaymentRenewalChargingSQSMessageExtractor paymentRenewalChargingSQSMessageExtractor,
                                                                                                  PaymentManager paymentManager, PaymentGatewayManager manager) {
        return new PaymentRenewalChargingConsumerPollingQueue(queueName,
                sqsClient,
                objectMapper,
                paymentRenewalChargingSQSMessageExtractor,
                paymentManager, threadPoolExecutor(2),
                scheduledThreadPoolExecutor(), manager);
    }

    @Bean
    public PaymentRecurringSchedulingPollingQueue paymentRecurringSchedulingPollingQueue (@Value("${payment.pooling.queue.schedule.name}") String queueName,
                                                                                          @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient, ObjectMapper objectMapper,
                                                                                          PaymentRecurringSchedulingSQSMessageExtractor paymentRecurringSchedulingSQSMessageExtractor,
                                                                                          PaymentManager paymentManager) {
        return new PaymentRecurringSchedulingPollingQueue(queueName,
                sqsClient,
                objectMapper,
                paymentRecurringSchedulingSQSMessageExtractor,
                paymentManager,
                threadPoolExecutor(2),
                scheduledThreadPoolExecutor());
    }

    @Bean
    public PaymentRecurringUnSchedulingPollingQueue paymentRecurringUnSchedulingPollingQueue (@Value("${payment.pooling.queue.unschedule.name}") String queueName,
                                                                                              @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                              ObjectMapper objectMapper,
                                                                                              PaymentRecurringUnSchedulingSQSMessageExtractor paymentRecurringUnSchedulingSQSMessageExtractor,
                                                                                              @Qualifier(in.wynk.payment.core.constant.BeanConstant.RECURRING_PAYMENT_RENEWAL_SERVICE)
                                                                                                      IRecurringPaymentManagerService recurringPaymentManager,
                                                                                              ITransactionManagerService transactionManagerService, ApplicationEventPublisher eventPublisher) {
        return new PaymentRecurringUnSchedulingPollingQueue(queueName,
                sqsClient,
                objectMapper,
                paymentRecurringUnSchedulingSQSMessageExtractor,
                threadPoolExecutor(2),
                scheduledThreadPoolExecutor(),
                recurringPaymentManager, transactionManagerService, eventPublisher);
    }

    @Bean
    public PaymentUserDeactivationPollingQueue paymentUserDeactivationPollingQueue (@Value("${payment.pooling.queue.userDeactivation.name}") String queueName,
                                                                                    @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                    ObjectMapper objectMapper,
                                                                                    PaymentUserDeactivationSQSMessageExtractor paymentUserDeactivationSQSMessageExtractor,
                                                                                    ApplicationEventPublisher eventPublisher) {
        return new PaymentUserDeactivationPollingQueue(queueName,
                sqsClient,
                objectMapper,
                paymentUserDeactivationSQSMessageExtractor,
                threadPoolExecutor(2),
                scheduledThreadPoolExecutor(), eventPublisher);
    }

    @Bean
    public PaymentRefundConsumerPollingQueue paymentRefundConsumerPollingQueue(@Value("${payment.pooling.queue.refund.name}") String queueName,
                                                                                    @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                    ObjectMapper objectMapper,
                                                                                    PaymentRefundSQSMessageExtractor paymentRefundSQSMessageExtractor,
                                                                                    PaymentManager paymentManager, ITransactionManagerService transactionManagerService,
                                                                               PaymentGatewayManager paymentGatewayManager) {
        return new PaymentRefundConsumerPollingQueue(queueName,
                sqsClient,
                objectMapper,
                paymentRefundSQSMessageExtractor,
                threadPoolExecutor(2),
                scheduledThreadPoolExecutor(), paymentManager, transactionManagerService, paymentGatewayManager);
    }

    @Bean
    public PaymentReconciliationSQSMessageExtractor paymentReconciliationSQSMessageExtractor (@Value("${payment.pooling.queue.reconciliation.name}") String queueName,
                                                                                              @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient) {
        return new PaymentReconciliationSQSMessageExtractor(queueName, sqsClient);
    }

    @Bean
    public PaymentRenewalSQSMessageExtractor paymentRenewalSQSMessageExtractor (@Value("${payment.pooling.queue.renewal.name}") String queueName,
                                                                                @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient) {
        return new PaymentRenewalSQSMessageExtractor(queueName, sqsClient);
    }

    @Bean
    public PreDebitNotificationSQSMessageExtractor preDebitNotificationSQSMessageExtractor (@Value("${payment.pooling.queue.preDebitNotification.name}") String queueName,
                                                                                            @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient) {
        return new PreDebitNotificationSQSMessageExtractor(queueName, sqsClient);
    }

    @Bean
    public PaymentRenewalChargingSQSMessageExtractor paymentRenewalChargingSQSMessageExtractor (@Value("${payment.pooling.queue.charging.name}") String queueName,
                                                                                                @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient) {
        return new PaymentRenewalChargingSQSMessageExtractor(queueName, sqsClient);
    }

    @Bean
    public PaymentRecurringSchedulingSQSMessageExtractor paymentRecurringSchedulingSQSMessageExtractor (@Value("${payment.pooling.queue.schedule.name}") String queueName,
                                                                                                        @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClients) {
        return new PaymentRecurringSchedulingSQSMessageExtractor(queueName, sqsClients);
    }

    @Bean
    public PaymentRecurringUnSchedulingSQSMessageExtractor paymentRecurringUnSchedulingSQSMessageExtractor (@Value("${payment.pooling.queue.unschedule.name}") String queueName,
                                                                                                            @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClients) {
        return new PaymentRecurringUnSchedulingSQSMessageExtractor(queueName, sqsClients);
    }

    @Bean
    public PaymentUserDeactivationSQSMessageExtractor paymentUserDeactivationSQSMessageExtractor (@Value("${payment.pooling.queue.userDeactivation.name}") String queueName,
                                                                                                  @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClients) {
        return new PaymentUserDeactivationSQSMessageExtractor(queueName, sqsClients);
    }

    @Bean
    public PurchaseAcknowledgementSQSMessageExtractor googlePlaySubscriptionAcknowledgementSQSMessageExtractor (@Value("${payment.pooling.queue.acknowledgement.name}") String queueName,
                                                                                                                @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClients) {
        return new PurchaseAcknowledgementSQSMessageExtractor(queueName, sqsClients);
    }

    @Bean
    public ExternalTransactionSQSMessageExtractor googlePlayExternalTransactionReportAcknowledgementSQSMessageExtractor (
            @Value("${payment.pooling.queue.externalTransaction.report.name}") String queueName,
            @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClients) {
        return new ExternalTransactionSQSMessageExtractor(queueName, sqsClients);
    }

    @Bean
    public PaymentRefundSQSMessageExtractor paymentRefundSQSMessageExtractor(@Value("${payment.pooling.queue.refund.name}") String queueName,
                                                                                                           @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClients) {
        return new PaymentRefundSQSMessageExtractor(queueName, sqsClients);
    }

    private ExecutorService threadPoolExecutor(int threadCount) {
        return Executors.newFixedThreadPool(threadCount);
    }

    private ScheduledExecutorService scheduledThreadPoolExecutor () {
        return Executors.newScheduledThreadPool(2);
    }

}
