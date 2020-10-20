package in.wynk.payment.service.impl;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.subscription.common.dto.AllPlansResponse;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.PlanProvisioningRequest;
import in.wynk.subscription.common.dto.PlanUnProvisioningRequest;
import in.wynk.subscription.common.message.SubscriptionProvisioningMessage;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;

import static in.wynk.payment.core.constant.BeanConstant.SUBSCRIPTION_SERVICE_S2S_TEMPLATE;

@Service
public class SubscriptionServiceManagerImpl implements ISubscriptionServiceManager {

    private final RestTemplate restTemplate;
    private final ISqsManagerService sqsMessagePublisher;

    @Value("${service.subscription.api.endpoint.allPlans}")
    private String allPlanApiEndPoint;

    @Value("${service.subscription.api.endpoint.subscribePlan}")
    private String subscribePlanEndPoint;

    @Value("${service.subscription.api.endpoint.unSubscribePlan}")
    private String unSubscribePlanEndPoint;

    public SubscriptionServiceManagerImpl(ISqsManagerService sqsMessagePublisher,
                                          @Qualifier(SUBSCRIPTION_SERVICE_S2S_TEMPLATE) RestTemplate restTemplate) {
        this.sqsMessagePublisher = sqsMessagePublisher;
        this.restTemplate = restTemplate;
    }

    @Override
    public List<PlanDTO> getPlans() {
        return Objects.requireNonNull(restTemplate.exchange(allPlanApiEndPoint, HttpMethod.GET, null, AllPlansResponse.class).getBody()).getData().getPlans();
    }

    @Override
    public void subscribePlanAsync(int planId, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus, PaymentEvent paymentEvent) {
        this.publishAsync(SubscriptionProvisioningMessage.builder()
                .uid(uid)
                .msisdn(msisdn)
                .planId(planId)
                .paymentPartner(BaseConstants.WYNK.toLowerCase())
                .referenceId(transactionId)
                .paymentEvent(paymentEvent)
                .transactionStatus(transactionStatus)
                .build());
    }

    @Override
    public void unSubscribePlanAsync(int planId, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus) {
        this.publishAsync(SubscriptionProvisioningMessage.builder()
                .uid(uid)
                .msisdn(msisdn)
                .planId(planId)
                .paymentPartner(BaseConstants.WYNK.toLowerCase())
                .referenceId(transactionId)
                .paymentEvent(PaymentEvent.UNSUBSCRIBE)
                .transactionStatus(transactionStatus)
                .build());
    }

    @Override
    public void subscribePlanSync(int planId, String sid, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus, PaymentEvent paymentEvent) {
        try {
            restTemplate.postForObject(String.format("%s/%s", subscribePlanEndPoint, planId),
                    PlanProvisioningRequest.builder()
                            .uid(uid)
                            .msisdn(msisdn)
                            .referenceId(transactionId)
                            .paymentPartner(BaseConstants.WYNK.toLowerCase())
                            .eventType(paymentEvent)
                            .build(),
                    String.class);
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e);
        }
    }

    @Override
    public void unSubscribePlanSync(int planId, String sid, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus) {
        try {
            restTemplate.postForObject(String.format("%s/%s", unSubscribePlanEndPoint, planId),
                    PlanUnProvisioningRequest.builder()
                            .uid(uid)
                            .referenceId(transactionId)
                            .paymentPartner(BaseConstants.WYNK.toLowerCase())
                            .build(),
                    String.class);
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e, "Unable to unSubscribe plan after successful payment");
        }
    }

    private void publishAsync(SubscriptionProvisioningMessage message) {
        try {
            sqsMessagePublisher.publishSQSMessage(message);
        } catch (Exception e) {
            throw new WynkRuntimeException(QueueErrorType.SQS001, e);
        }
    }

}
