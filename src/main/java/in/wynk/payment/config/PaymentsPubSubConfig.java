package in.wynk.payment.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import in.wynk.payment.consumer.*;
import in.wynk.payment.extractor.*;
import in.wynk.payment.service.*;
import in.wynk.payment.service.impl.RecurringPaymentManager;
import in.wynk.payment.utils.RecurringTransactionUtils;
import in.wynk.pubsub.service.IPubSubManagerService;
import in.wynk.wynkservice.core.dao.entity.App;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

//@Configuration
public class PaymentsPubSubConfig {

    /*@Value("${payments.pubSub.queue.enabled:false}")
    private boolean enabled;

    @Bean
    public PaymentReconciliationGCPConsumer paymentReconciliationGCPConsumer(@Value("${payments.pooling.pubSub.reconciliation.projectName}") String projectName,
                                                                             @Value("${payments.pooling.pubSub.reconciliation.topicName}") String topicName,
                                                                             @Value("${payments.pooling.pubSub.reconciliation.subscriptionName}") String subscriptionName,
                                                                             @Value("${payments.pooling.pubSub.reconciliation.bufferInterval}") String bufferInterval,
                                                                             ObjectMapper objectMapper, ITransactionManagerService transactionManager, ApplicationEventPublisher eventPublisher) {
        if(enabled){
            return new PaymentReconciliationGCPConsumer(
                    projectName, topicName, subscriptionName,
                    objectMapper,
                    threadPoolExecutor(4),
                    scheduledThreadPoolExecutor(), transactionManager, eventPublisher);
        }
        return null;
    }

   *//* @Bean
    public PaymentReconciliationGCPMessageExtractor paymentReconciliationGCPMessageExtractor(@Value("${payments.pooling.pubSub.reconciliation.projectName}") String projectName,
                                                                                             @Value("${payments.pooling.pubSub.reconciliation.subscriptionName}") String subscriptionName,
                                                                                             @Value("${payments.pooling.pubSub.reconciliation.bufferInterval}") String bufferInterval) {
        return new PaymentReconciliationGCPMessageExtractor(projectName, subscriptionName, bufferInterval);
    }*//*

    @Bean
    public PurchaseAcknowledgementGCPConsumer purchaseAcknowledgementGCPConsumer(@Value("${payments.pooling.pubSub.acknowledgement.projectName}") String projectName,
                                                                                 @Value("${payments.pooling.pubSub.acknowledgement.topicName}") String topicName,
                                                                                 @Value("${payments.pooling.pubSub.acknowledgement.subscriptionName}") String subscriptionName,
                                                                                 @Value("${payments.pooling.pubSub.acknowledgement.bufferInterval}") String bufferInterval,
                                                                                 ObjectMapper objectMapper) {
        if(enabled){
            return new PurchaseAcknowledgementGCPConsumer(
                    projectName, topicName, subscriptionName,
                    objectMapper,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor());
        }
        return null;
    }

    *//*@Bean
    public PurchaseAcknowledgementGCPMessageExtractor purchaseAcknowledgementGCPMessageExtractor(@Value("${payments.pooling.pubSub.acknowledgement.projectName}") String projectName,
                                                                                                 @Value("${payments.pooling.pubSub.acknowledgement.subscriptionName}") String subscriptionName,
                                                                                                 @Value("${payments.pooling.pubSub.acknowledgement.bufferInterval}") String bufferInterval) {
        return new PurchaseAcknowledgementGCPMessageExtractor(projectName, subscriptionName, bufferInterval);
    }*//*

    @Bean
    public ExternalTransactionReportGCPConsumer ExternalTransactionReportGCPConsumer(@Value("${payments.pooling.pubSub.externalTransaction.report.projectName}") String projectName,
                                                                                     @Value("${payments.pooling.pubSub.externalTransaction.report.topicName}") String topicName,
                                                                                     @Value("${payments.pooling.pubSub.externalTransaction.report.subscriptionName}") String subscriptionName,
                                                                                     @Value("${payments.pooling.pubSub.externalTransaction.report.bufferInterval}") String bufferInterval,
                                                                                     ObjectMapper objectMapper) {
        if(enabled){
            return new ExternalTransactionReportGCPConsumer(
                    projectName, topicName, subscriptionName,
                    objectMapper,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor());
        }
        return null;
    }

    *//*@Bean
    public ExternalTransactionPubSubMessageExtractor externalTransactionPubSubMessageExtractor(@Value("${payments.pooling.pubSub.externalTransaction.report.projectName}") String projectName,
                                                                                               @Value("${payments.pooling.pubSub.externalTransaction.report.subscriptionName}") String subscriptionName,
                                                                                               @Value("${payments.pooling.pubSub.externalTransaction.report.bufferInterval}") String bufferInterval) {
        return new ExternalTransactionPubSubMessageExtractor(projectName, subscriptionName, bufferInterval);
    }*//*

    @Bean
    public PaymentRenewalGCPConsumer paymentRenewalConsumer(@Value("${payments.pooling.pubSub.renewal.projectName}") String projectName,
                                                            @Value("${payments.pooling.pubSub.renewal.topicName}") String topicName,
                                                            @Value("${payments.pooling.pubSub.renewal.subscriptionName}") String subscriptionName,
                                                            @Value("${payments.pooling.pubSub.renewal.bufferInterval}") String bufferInterval,
                                                            ObjectMapper objectMapper, ITransactionManagerService transactionManager, ISubscriptionServiceManager subscriptionServiceManager,
                                                            IPubSubManagerService pubSubManagerService,
                                                            IRecurringPaymentManagerService recurringPaymentManagerService, PaymentCachingService cachingService, RecurringTransactionUtils recurringTransactionUtils) {
        if(enabled){
            return new PaymentRenewalGCPConsumer(
                    projectName, topicName, subscriptionName,
                    objectMapper,
                    pubSubManagerService,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor(), transactionManager, subscriptionServiceManager, recurringPaymentManagerService, cachingService, recurringTransactionUtils);
        }
        return null;
    }

    *//*@Bean
    public PaymentRenewalPubSubMessageExtractor paymentRenewalPubSubMessageExtractor(@Value("${payments.pooling.pubSub.renewal.projectName}") String projectName,
                                                                                     @Value("${payments.pooling.pubSub.renewal.subscriptionName}") String subscriptionName,
                                                                                     @Value("${payments.pooling.pubSub.renewal.bufferInterval}") String bufferInterva) {
        return new PaymentRenewalPubSubMessageExtractor(projectName, subscriptionName, bufferInterva);
    }*//*


    @Bean
    public PaymentRenewalChargingGCPConsumer paymentRenewalChargingGCPConsumer(@Value("${payments.pooling.pubSub.charging.projectName}") String projectName,
                                                                                         @Value("${payments.pooling.pubSub.charging.topicName}") String topicName,
                                                                                         @Value("${payments.pooling.pubSub.charging.subscriptionName}") String subscriptionName,
                                                                                         @Value("${payments.pooling.pubSub.charging.bufferInterval}") String bufferInterval,
                                                                                         ObjectMapper objectMapper, PaymentManager paymentManager, PaymentGatewayManager manager) {
        if(enabled){
            return new PaymentRenewalChargingGCPConsumer(
                    projectName, topicName, subscriptionName,
                    objectMapper,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor(), paymentManager, manager);
        }
        return null;
    }

    *//*@Bean
    public PaymentRenewalChargingPubSubMessageExtractor paymentRenewalChargingPubSubMessageExtractor(@Value("${payments.pooling.pubSub.charging.projectName}") String projectName,
                                                                                                 @Value("${payments.pooling.pubSub.charging.subscriptionName}") String subscriptionName,
                                                                                                 @Value("${payments.pooling.pubSub.charging.bufferInterval}") String bufferInterval) {
        return new PaymentRenewalChargingPubSubMessageExtractor(projectName, subscriptionName, bufferInterval);
    }*//*

    @Bean
    public PaymentRecurringSchedulingGCPConsumer paymentRecurringSchedulingGCPConsumer(@Value("${payments.pooling.pubSub.schedule.projectName}") String projectName,
                                                                                       @Value("${payments.pooling.pubSub.schedule.topicName}") String topicName,
                                                                                       @Value("${payments.pooling.pubSub.schedule.subscriptionName}") String subscriptionName,
                                                                                       @Value("${payments.pooling.pubSub.schedule.bufferInterval}") String bufferInterval,
                                                                                        ObjectMapper objectMapper, PaymentManager paymentManager) {
        if(enabled){
            return new PaymentRecurringSchedulingGCPConsumer(
                    projectName, topicName, subscriptionName,
                    objectMapper,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor(), paymentManager);
        }
        return null;
    }

   *//* @Bean
    public PaymentRecurringSchedulingPubSubMessageExtractor paymentRecurringSchedulingPubSubMessageExtractor(@Value("${payments.pooling.pubSub.schedule.projectName}") String projectName,
                                                                                                     @Value("${payments.pooling.pubSub.schedule.subscriptionName}") String subscriptionName,
                                                                                                     @Value("${payments.pooling.pubSub.schedule.bufferInterval}") String bufferInterval) {
        return new PaymentRecurringSchedulingPubSubMessageExtractor(projectName, subscriptionName, bufferInterval);
    }*//*


    @Bean
    public PaymentRecurringUnSchedulingGCPConsumer paymentRecurringUnSchedulingGCPConsumer(@Value("${payments.pooling.pubSub.unschedule.projectName}") String projectName,
                                                                                           @Value("${payments.pooling.pubSub.unschedule.topicName}") String topicName,
                                                                                           @Value("${payments.pooling.pubSub.unschedule.subscriptionName}") String subscriptionName,
                                                                                           @Value("${payments.pooling.pubSub.unschedule.bufferInterval}") String bufferInterval,
                                                                                           ObjectMapper objectMapper,
                                                                                           @Qualifier(in.wynk.payment.core.constant.BeanConstant.RECURRING_PAYMENT_RENEWAL_SERVICE) IRecurringPaymentManagerService recurringPaymentManager, ITransactionManagerService transactionManagerService, ApplicationEventPublisher eventPublisher) {
        if(enabled){
            return new PaymentRecurringUnSchedulingGCPConsumer(
                    projectName, topicName, subscriptionName,
                    objectMapper,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor(), recurringPaymentManager,transactionManagerService,eventPublisher);
        }
        return null;
    }

    *//*@Bean
    public PaymentRecurringUnSchedulingPubSubMessageExtractor paymentRecurringUnSchedulingPubSubMessageExtractor(@Value("${payments.pooling.pubSub.unschedule.projectName}") String projectName,
                                                                                                             @Value("${payments.pooling.pubSub.unschedule.subscriptionName}") String subscriptionName,
                                                                                                             @Value("${payments.pooling.pubSub.unschedule.bufferInterval}") String bufferInterval) {
        return new PaymentRecurringUnSchedulingPubSubMessageExtractor(projectName, subscriptionName, bufferInterval);
    }*//*


    @Bean
    public PreDebitNotificationGCPConsumer preDebitNotificationGCPConsumer(@Value("${payments.pooling.pubSub.preDebitNotification.projectName}") String projectName,
                                                                                       @Value("${payments.pooling.pubSub.preDebitNotification.topicName}") String topicName,
                                                                                       @Value("${payments.pooling.pubSub.preDebitNotification.subscriptionName}") String subscriptionName,
                                                                                       @Value("${payments.pooling.pubSub.preDebitNotification.bufferInterval}") String bufferInterval,
                                                                                       ObjectMapper objectMapper, PaymentGatewayManager manager, RecurringPaymentManager recurringPaymentManager) {
        if(enabled){
            return new PreDebitNotificationGCPConsumer(
                    projectName, topicName, subscriptionName,
                    objectMapper,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor(), manager, recurringPaymentManager);
        }
        return null;
    }

   *//* @Bean
    public PreDebitNotificationPubSubMessageExtractor preDebitNotificationPubSubMessageExtractor(@Value("${payments.pooling.pubSub.preDebitNotification.projectName}") String projectName,
                                                                                                             @Value("${payments.pooling.pubSub.preDebitNotification.subscriptionName}") String subscriptionName,
                                                                                                             @Value("${payments.pooling.pubSub.preDebitNotification.bufferInterval}") String bufferInterval) {
        return new PreDebitNotificationPubSubMessageExtractor(projectName, subscriptionName, bufferInterval);
    }*//*


    @Bean
    public PaymentRefundGCPConsumer paymentRefundGCPConsumer(@Value("${payments.pooling.pubSub.refund.projectName}") String projectName,
                                                                                       @Value("${payments.pooling.pubSub.refund.topicName}") String topicName,
                                                                                       @Value("${payments.pooling.pubSub.refund.subscriptionName}") String subscriptionName,
                                                                                       @Value("${payments.pooling.pubSub.refund.bufferInterval}") String bufferInterval,
                                                                                       ObjectMapper objectMapper, PaymentManager paymentManager) {
        if(enabled){
            return new PaymentRefundGCPConsumer(
                    projectName, topicName, subscriptionName,
                    objectMapper,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor(), paymentManager);
        }
        return null;
    }

    *//*@Bean
    public PaymentRefundPubSubMessageExtractor paymentRefundPubSubMessageExtractor(@Value("${payments.pooling.pubSub.refund.projectName}") String projectName,
                                                                                                             @Value("${payments.pooling.pubSub.refund.subscriptionName}") String subscriptionName,
                                                                                                             @Value("${payments.pooling.pubSub.refund.bufferInterval}") String bufferInterval) {
        return new PaymentRefundPubSubMessageExtractor(projectName, subscriptionName, bufferInterval);
    }*//*


    @Bean
    public PaymentUserDeactivationGCPConsumer paymentUserDeactivationGCPConsumer(@Value("${payments.pooling.pubSub.userDeactivation.projectName}") String projectName,
                                                             @Value("${payments.pooling.pubSub.userDeactivation.topicName}") String topicName,
                                                             @Value("${payments.pooling.pubSub.userDeactivation.subscriptionName}") String subscriptionName,
                                                             @Value("${payments.pooling.pubSub.userDeactivation.bufferInterval}") String bufferInterval,
                                                             ObjectMapper objectMapper, ApplicationEventPublisher eventPublisher) {
        if(enabled){
            return new PaymentUserDeactivationGCPConsumer(
                    projectName, topicName, subscriptionName,
                    objectMapper,
                    threadPoolExecutor(2),
                    scheduledThreadPoolExecutor(), eventPublisher);
        }
        return null;
    }

    *//*@Bean
    public PaymentUserDeactivationPubSubMessageExtractor paymentUserDeactivationPubSubMessageExtractor(@Value("${payments.pooling.pubSub.userDeactivation.projectName}") String projectName,
                                                                                   @Value("${payments.pooling.pubSub.userDeactivation.subscriptionName}") String subscriptionName,
                                                                                   @Value("${payments.pooling.pubSub.userDeactivation.bufferInterval}") String bufferInterval) {
        return new PaymentUserDeactivationPubSubMessageExtractor(projectName, subscriptionName, bufferInterval);
    }*//*







    private ExecutorService threadPoolExecutor(int threadCount) {
        return Executors.newFixedThreadPool(threadCount);
    }

    private ScheduledExecutorService scheduledThreadPoolExecutor() {
        return Executors.newScheduledThreadPool(2);
    }
*/
}
