package in.wynk.payment.config;

import com.amazonaws.services.sqs.AmazonSQS;
import in.wynk.payment.consumer.PaymentReconciliationConsumerPollingQueue;
import in.wynk.payment.extractor.PaymentReconciliationSQSMessageExtractor;
import in.wynk.queue.constant.BeanConstant;
import in.wynk.queue.producer.ISQSMessagePublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.*;

@Configuration
public class PaymentQueuesConfig {

    @Bean
    public PaymentReconciliationConsumerPollingQueue paymentReconciliationConsumerPollingQueue(@Value("${payment.pooling.queue.reconciliation.name}") String queueName,
                                                                                               @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient,
                                                                                               @Qualifier(in.wynk.queue.constant.BeanConstant.SQS_EVENT_PRODUCER) ISQSMessagePublisher sqsMessagePublisher,
                                                                                               ApplicationContext applicationContext,
                                                                                               PaymentReconciliationSQSMessageExtractor paymentReconciliationSQSMessageExtractor) {
        return new PaymentReconciliationConsumerPollingQueue(queueName,
                sqsClient,
                paymentReconciliationSQSMessageExtractor,
                (ThreadPoolExecutor) threadPoolExecutor(),
                (ScheduledThreadPoolExecutor) scheduledThreadPoolExecutor(),
                sqsMessagePublisher,
                applicationContext);
    }

    @Bean
    public PaymentReconciliationSQSMessageExtractor paymentReconciliationSQSMessageExtractor(@Value("${payment.pooling.queue.reconciliation.name}") String queueName,
                                                                                             @Qualifier(BeanConstant.SQS_MANAGER) AmazonSQS sqsClient) {
        return new PaymentReconciliationSQSMessageExtractor(queueName, sqsClient);
    }

    private ExecutorService threadPoolExecutor() {
        return Executors.newFixedThreadPool(2);
    }

    private ScheduledExecutorService scheduledThreadPoolExecutor() {
        return Executors.newScheduledThreadPool(2);
    }

}
