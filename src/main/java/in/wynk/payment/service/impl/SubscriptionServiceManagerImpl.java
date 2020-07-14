package in.wynk.payment.service.impl;

import in.wynk.commons.dto.AllPlansResponse;
import in.wynk.commons.dto.PlanDTO;
import in.wynk.commons.dto.SubscriptionProvisioningMessage;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.http.template.HttpTemplate;
import in.wynk.payment.core.constant.BeanConstant;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SubscriptionServiceManagerImpl implements ISubscriptionServiceManager {

    private final HttpTemplate httpTemplate;
    private final ISQSMessagePublisher sqsMessagePublisher;

    @Value("${payment.pooling.queue.subscription.name}")
    private String subscriptionQueue;

    @Value("${payment.pooling.queue.subscription.sqs.producer.delayInSecond}")
    private int subscriptionMessageDelay;

    @Value("${service.subscription.api.endpoint.allPlans}")
    private String allPlanApiEndPoint;

    public SubscriptionServiceManagerImpl(ISQSMessagePublisher sqsMessagePublisher,
                                          @Qualifier(BeanConstant.SUBSCRIPTION_SERVICE_S2S_TEMPLATE) HttpTemplate httpTemplate) {
        this.sqsMessagePublisher = sqsMessagePublisher;
        this.httpTemplate = httpTemplate;
    }

    @Override
    public List<PlanDTO> getPlans() {
        return httpTemplate.getForObject(allPlanApiEndPoint, AllPlansResponse.class).map(AllPlansResponse::getData).orElseThrow(() -> new WynkRuntimeException(PaymentErrorType.PAY013));
    }

    @Override
    public String publish(int planId, String uid, String transactionId, TransactionStatus transactionStatus, TransactionEvent transactionEvent) {
        try {
            return sqsMessagePublisher.publish(SendSQSMessageRequest.<SubscriptionProvisioningMessage>builder()
                    .queueName(subscriptionQueue)
                    .delaySeconds(subscriptionMessageDelay)
                    .message(SubscriptionProvisioningMessage.builder()
                            .uid(uid)
                            .planId(planId)
                            .transactionId(transactionId)
                            .transactionEvent(transactionEvent)
                            .transactionStatus(transactionStatus)
                            .build())
                    .build());
        } catch (Exception e) {
            throw new WynkRuntimeException(QueueErrorType.SQS001, e);
        }
    }
}
