package in.wynk.payment.config;

import com.amazonaws.services.sqs.AmazonSQS;
import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.consumer.*;
import in.wynk.payment.extractor.*;
import in.wynk.payment.service.*;
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

    @Value("${payments.sqs.queue.enabled:false}")
    private boolean enabled;

    @Bean
    public PaymentReconciliationConsumerPollingQueue paymentReconciliationConsumerPollingQueue(@Value("${payment.pooling.queue.reconciliation.name}") String queueName,
                                                                                               @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                               ObjectMapper objectMapper,
                                                                                               PaymentReconciliationSQSMessageExtractor paymentReconciliationSQSMessageExtractor, ITransactionManagerService transactionManager, ApplicationEventPublisher eventPublisher) {
        if(enabled) {
            return new PaymentReconciliationConsumerPollingQueue(queueName,
                    sqsClient,
                    objectMapper,
                    paymentReconciliationSQSMessageExtractor,
                    threadPoolExecutor(4),
                    scheduledThreadPoolExecutor(), transactionManager, eventPublisher);
        }
        return null;
    }

    @Bean
    public PurchaseAcknowledgementConsumerPollingQueue purchaseAcknowledgementConsumerPollingQueue(@Value("${payment.pooling.queue.acknowledgement.name}") String queueName,
                                                                                                       @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                                       ObjectMapper objectMapper,
                                                                                                       PurchaseAcknowledgementSQSMessageExtractor purchaseAcknowledgementSQSMessageExtractor) {
        if(enabled) {
            return new PurchaseAcknowledgementConsumerPollingQueue(queueName,
                    sqsClient,
                    objectMapper,
                    purchaseAcknowledgementSQSMessageExtractor,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor());
        }
        return null;
    }

    @Bean
    public ExternalTransactionReportConsumerPollingQueue externalTransactionReportConsumerPollingQueue (@Value("${payment.pooling.queue.externalTransaction.report.name}") String queueName,
                                                                                                        @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                                        ObjectMapper objectMapper,
                                                                                                        ExternalTransactionSQSMessageExtractor externalTransactionSQSMessageExtractor) {
        if(enabled) {
            return new ExternalTransactionReportConsumerPollingQueue(queueName,
                    sqsClient,
                    objectMapper,
                    externalTransactionSQSMessageExtractor,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor());
        }
        return null;
    }

    @Bean
    public PaymentRenewalConsumerPollingQueue paymentRenewalConsumerPollingQueue (@Value("${payment.pooling.queue.renewal.name}") String queueName,
                                                                                  @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                  ObjectMapper objectMapper,
                                                                                  PaymentRenewalSQSMessageExtractor paymentRenewalSQSMessageExtractor,
                                                                                  ISqsManagerService sqsManagerService,
                                                                                  ITransactionManagerService transactionManager,
                                                                                  ISubscriptionServiceManager subscriptionServiceManager,
                                                                                  IRecurringPaymentManagerService recurringPaymentManagerService, PaymentCachingService cachingService) {
        if(enabled) {
            return new PaymentRenewalConsumerPollingQueue(queueName,
                    sqsClient,
                    objectMapper,
                    paymentRenewalSQSMessageExtractor,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor(),
                    sqsManagerService,
                    transactionManager,
                    subscriptionServiceManager, recurringPaymentManagerService, cachingService);
        }
        return null;
    }

    @Bean
    public PreDebitNotificationConsumerPollingQueue preDebitNotificationConsumerPollingQueue (@Value("${payment.pooling.queue.preDebitNotification.name}") String queueName,
                                                                                              @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                              ObjectMapper objectMapper,
                                                                                              PaymentGatewayManager manager,
                                                                                              PreDebitNotificationSQSMessageExtractor preDebitNotificationSQSMessageExtractor) {
        if(enabled) {
            return new PreDebitNotificationConsumerPollingQueue(queueName,
                    sqsClient,
                    objectMapper,
                    preDebitNotificationSQSMessageExtractor,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor(), manager);
        }
        return null;
    }

    @Bean
    public PaymentRenewalChargingConsumerPollingQueue paymentRenewalChargingConsumerPollingQueue (@Value("${payment.pooling.queue.charging.name}") String queueName,
                                                                                                  @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                                  ObjectMapper objectMapper,
                                                                                                  PaymentRenewalChargingSQSMessageExtractor paymentRenewalChargingSQSMessageExtractor,
                                                                                                  PaymentManager paymentManager, PaymentGatewayManager manager) {
        if(enabled) {
            return new PaymentRenewalChargingConsumerPollingQueue(queueName,
                    sqsClient,
                    objectMapper,
                    paymentRenewalChargingSQSMessageExtractor,
                    paymentManager, threadPoolExecutor(2),
                    scheduledThreadPoolExecutor(), manager);
        }
        return null;
    }

    @Bean
    public PaymentRecurringSchedulingPollingQueue paymentRecurringSchedulingPollingQueue (@Value("${payment.pooling.queue.schedule.name}") String queueName,
                                                                                          @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient, ObjectMapper objectMapper,
                                                                                          PaymentRecurringSchedulingSQSMessageExtractor paymentRecurringSchedulingSQSMessageExtractor,
                                                                                          PaymentManager paymentManager) {
        if(enabled) {
            return new PaymentRecurringSchedulingPollingQueue(queueName,
                    sqsClient,
                    objectMapper,
                    paymentRecurringSchedulingSQSMessageExtractor,
                    paymentManager,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor());
        }
        return null;
    }

    @Bean
    public PaymentRecurringUnSchedulingPollingQueue paymentRecurringUnSchedulingPollingQueue (@Value("${payment.pooling.queue.unschedule.name}") String queueName,
                                                                                              @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                              ObjectMapper objectMapper,
                                                                                              PaymentRecurringUnSchedulingSQSMessageExtractor paymentRecurringUnSchedulingSQSMessageExtractor,
                                                                                              @Qualifier(in.wynk.payment.core.constant.BeanConstant.RECURRING_PAYMENT_RENEWAL_SERVICE)
                                                                                                      IRecurringPaymentManagerService recurringPaymentManager,
                                                                                              ITransactionManagerService transactionManagerService, ApplicationEventPublisher eventPublisher) {
        if(enabled){
        return new PaymentRecurringUnSchedulingPollingQueue(queueName,
                sqsClient,
                objectMapper,
                paymentRecurringUnSchedulingSQSMessageExtractor,
                threadPoolExecutor(2),
                scheduledThreadPoolExecutor(),
                recurringPaymentManager, transactionManagerService, eventPublisher);
        }
        return null;
    }

    @Bean
    public PaymentUserDeactivationPollingQueue paymentUserDeactivationPollingQueue (@Value("${payment.pooling.queue.userDeactivation.name}") String queueName,
                                                                                    @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                    ObjectMapper objectMapper,
                                                                                    PaymentUserDeactivationSQSMessageExtractor paymentUserDeactivationSQSMessageExtractor,
                                                                                    ApplicationEventPublisher eventPublisher) {
        if(enabled) {
            return new PaymentUserDeactivationPollingQueue(queueName,
                    sqsClient,
                    objectMapper,
                    paymentUserDeactivationSQSMessageExtractor,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor(), eventPublisher);
        }
        return null;
    }

    @Bean
    public PaymentRefundConsumerPollingQueue paymentRefundConsumerPollingQueue(@Value("${payment.pooling.queue.refund.name}") String queueName,
                                                                                    @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                    ObjectMapper objectMapper,
                                                                                    PaymentRefundSQSMessageExtractor paymentRefundSQSMessageExtractor,
                                                                                    PaymentManager paymentManager) {
        if(enabled) {
            return new PaymentRefundConsumerPollingQueue(queueName,
                    sqsClient,
                    objectMapper,
                    paymentRefundSQSMessageExtractor,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor(), paymentManager);
        }
        return null;
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
