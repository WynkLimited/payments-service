package in.wynk.payment.service.impl;

import in.wynk.commons.dto.*;
import in.wynk.commons.enums.TransactionEvent;
import in.wynk.commons.enums.TransactionStatus;
import in.wynk.exception.WynkErrorType;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.http.template.HttpTemplate;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.dto.SendSQSMessageRequest;
import in.wynk.queue.producer.ISQSMessagePublisher;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;

import java.util.List;

import static in.wynk.payment.core.constant.BeanConstant.SUBSCRIPTION_SERVICE_S2S_TEMPLATE;

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

    @Value("${service.subscription.api.endpoint.subscribePlan}")
    private String subscribePlanEndPoint;

    @Value("${service.subscription.api.endpoint.unSubscribePlan}")
    private String unSubscribePlanEndPoint;

    public SubscriptionServiceManagerImpl(ISQSMessagePublisher sqsMessagePublisher,
                                          @Qualifier(SUBSCRIPTION_SERVICE_S2S_TEMPLATE) HttpTemplate httpTemplate) {
        this.sqsMessagePublisher = sqsMessagePublisher;
        this.httpTemplate = httpTemplate;
    }

    @Override
    public List<PlanDTO> getPlans() {
        return httpTemplate.exchange(allPlanApiEndPoint, HttpMethod.GET, null, AllPlansResponse.class)
                .map(HttpEntity::getBody)
                .map(AllPlansResponse::getData)
                .map(AllPlansResponse.AllPlans::getPlans)
                .orElseThrow(() -> new WynkRuntimeException(WynkErrorType.RG777));
    }

    @Override
    public void subscribePlanAsync(int planId, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus, TransactionEvent transactionEvent) {
        this.publishAsync(SubscriptionProvisioningMessage.builder()
                .uid(uid)
                .msisdn(msisdn)
                .planId(planId)
                .transactionId(transactionId)
                .transactionEvent(transactionEvent)
                .transactionStatus(transactionStatus)
                .build());
    }

    @Override
    public void unSubscribePlanAsync(int planId, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus) {
        this.publishAsync(SubscriptionProvisioningMessage.builder()
                .uid(uid)
                .msisdn(msisdn)
                .planId(planId)
                .transactionId(transactionId)
                .transactionEvent(TransactionEvent.UNSUBSCRIBE)
                .transactionStatus(transactionStatus)
                .build());
    }

    @Override
    public void subscribePlanSync(int planId, String sid, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus, TransactionEvent transactionEvent) {
        try {
            httpTemplate.postForObject(subscribePlanEndPoint + sid,
                    SubscriptionProvisioningRequest.builder()
                            .uid(uid)
                            .planId(planId)
                            .msisdn(msisdn)
                            .transactionId(transactionId)
                            .eventType(transactionEvent)
                            .build(),
                    String.class);
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e);
        }
    }

    @Override
    public void unSubscribePlanSync(int planId, String sid, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus) {
        try {
            httpTemplate.postForObject(unSubscribePlanEndPoint + sid,
                    SubscriptionUnProvisioningRequest.builder()
                            .uid(uid)
                            .planId(planId)
                            .build(),
                    String.class);
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e, "Unable to unSubscribe plan after successful payment");
        }
    }

    private void publishAsync(SubscriptionProvisioningMessage message) {
        try {
            sqsMessagePublisher.publish(SendSQSMessageRequest.<SubscriptionProvisioningMessage>builder()
                    .queueName(subscriptionQueue)
                    .delaySeconds(subscriptionMessageDelay)
                    .message(message)
                    .build());
        } catch (Exception e) {
            throw new WynkRuntimeException(QueueErrorType.SQS001, e);
        }
    }

}
