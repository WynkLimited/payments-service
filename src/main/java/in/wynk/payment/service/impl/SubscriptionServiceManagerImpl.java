package in.wynk.payment.service.impl;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.context.WynkApplicationContext;
import in.wynk.common.dto.WynkResponse;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.enums.TransactionStatus;
import in.wynk.common.utils.ChecksumUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.subscription.common.dto.*;
import in.wynk.subscription.common.enums.ProvisionState;
import in.wynk.subscription.common.message.SubscriptionProvisioningMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Objects;

import static in.wynk.payment.core.constant.BeanConstant.SUBSCRIPTION_SERVICE_S2S_TEMPLATE;

@Service
@Slf4j
public class SubscriptionServiceManagerImpl implements ISubscriptionServiceManager {

    @Value("${service.subscription.api.endpoint.allPlans}")
    private String allPlanApiEndPoint;

    @Value("${service.subscription.api.endpoint.subscribePlan}")
    private String subscribePlanEndPoint;

    @Value("${service.subscription.api.endpoint.unSubscribePlan}")
    private String unSubscribePlanEndPoint;

    private final RestTemplate restTemplate;
    private final ISqsManagerService sqsMessagePublisher;
    private final WynkApplicationContext myApplicationContext;

    public SubscriptionServiceManagerImpl(@Qualifier(SUBSCRIPTION_SERVICE_S2S_TEMPLATE) RestTemplate restTemplate,
                                          ISqsManagerService sqsMessagePublisher,
                                          WynkApplicationContext myApplicationContext) {
        this.restTemplate = restTemplate;
        this.sqsMessagePublisher = sqsMessagePublisher;
        this.myApplicationContext = myApplicationContext;
    }

    @Override
    public List<PlanDTO> getPlans() {
        RequestEntity<Void> allPlanRequest = ChecksumUtils.buildEntityWithAuthHeaders(allPlanApiEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), null, HttpMethod.GET);
        return Objects.requireNonNull(restTemplate.exchange(allPlanRequest, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<AllPlansResponse>>() {
        }).getBody()).getData().getPlans();
    }

    @Override
    public void subscribePlanAsync(int planId, String transactionId, String uid, String msisdn, String paymentCode, TransactionStatus transactionStatus, PaymentEvent paymentEvent) {
        this.publishAsync(SubscriptionProvisioningMessage.builder()
                .uid(uid)
                .msisdn(msisdn)
                .planId(planId)
                .paymentCode(paymentCode)
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
    public void subscribePlanSync(int planId, String sid, String transactionId, String uid, String msisdn, String paymentCode, TransactionStatus transactionStatus, PaymentEvent paymentEvent) {
        try {
            PlanProvisioningRequest planProvisioningRequest = SinglePlanProvisionRequest.builder()
                    .uid(uid)
                    .planId(planId)
                    .msisdn(msisdn)
                    .paymentCode(paymentCode)
                    .referenceId(transactionId)
                    .paymentPartner(BaseConstants.WYNK.toLowerCase())
                    .eventType(paymentEvent)
                    .build();
            RequestEntity<PlanProvisioningRequest> requestEntity = ChecksumUtils.buildEntityWithAuthHeaders(subscribePlanEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), planProvisioningRequest, HttpMethod.POST);
            ResponseEntity<WynkResponse.WynkResponseWrapper<PlanProvisioningResponse>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<PlanProvisioningResponse>>() {
            });
            if (Objects.nonNull(response.getBody()) && Objects.nonNull(response.getBody().getData())) {
                PlanProvisioningResponse provisioningResponse = response.getBody().getData();
                if (provisioningResponse.getState() != ProvisionState.SUBSCRIBED) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY013);
                }
            }
        } catch (HttpStatusCodeException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e, e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.PAYMENT_ERROR, "Error occurred while subscribing {}", e.getMessage(), e);
            throw new WynkRuntimeException(e);
        }
    }

    @Override
    public void unSubscribePlanSync(int planId, String sid, String transactionId, String uid, String msisdn, TransactionStatus transactionStatus) {
        try {
            PlanUnProvisioningRequest unProvisioningRequest = PlanUnProvisioningRequest.builder()
                    .uid(uid)
                    .planId(planId)
                    .referenceId(transactionId)
                    .paymentPartner(BaseConstants.WYNK.toLowerCase())
                    .build();
            RequestEntity<PlanUnProvisioningRequest> requestEntity = ChecksumUtils.buildEntityWithAuthHeaders(unSubscribePlanEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), unProvisioningRequest, HttpMethod.POST);
            ResponseEntity<WynkResponse.WynkResponseWrapper<PlanProvisioningResponse>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<PlanProvisioningResponse>>() {
            });
            if (Objects.nonNull(response.getBody()) && Objects.nonNull(response.getBody().getData())) {
                PlanProvisioningResponse provisioningResponse = response.getBody().getData();
                if (provisioningResponse.getState() != ProvisionState.UNSUBSCRIBED) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY013);
                }
            }
        } catch (HttpStatusCodeException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e, e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e, "Unable to unSubscribe plan from payment service");
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
