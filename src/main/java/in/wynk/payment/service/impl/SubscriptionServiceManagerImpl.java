package in.wynk.payment.service.impl;

import in.wynk.common.constant.BaseConstants;
import in.wynk.common.context.WynkApplicationContext;
import in.wynk.common.dto.WynkResponse;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.utils.ChecksumUtils;
import in.wynk.common.utils.MsisdnUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.dto.IAppDetails;
import in.wynk.payment.dto.IUserDetails;
import in.wynk.payment.dto.request.*;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.queue.service.ISqsManagerService;
import in.wynk.subscription.common.dto.*;
import in.wynk.subscription.common.enums.ProvisionState;
import in.wynk.subscription.common.message.SubscriptionProvisioningMessage;
import in.wynk.subscription.common.request.PlanProvisioningRequest;
import in.wynk.subscription.common.request.PlanUnProvisioningRequest;
import in.wynk.subscription.common.request.SinglePlanProvisionRequest;
import in.wynk.subscription.common.request.TrialPlansComputationRequest;
import in.wynk.subscription.common.response.AllItemsResponse;
import in.wynk.subscription.common.response.AllPlansResponse;
import in.wynk.subscription.common.response.PlanProvisioningResponse;
import in.wynk.subscription.common.response.TrialPlanComputationResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.util.*;
import java.util.stream.Collectors;

import static in.wynk.payment.core.constant.BeanConstant.SUBSCRIPTION_SERVICE_S2S_TEMPLATE;

@Service
@Slf4j
public class SubscriptionServiceManagerImpl implements ISubscriptionServiceManager {

    @Value("${service.subscription.api.endpoint.allPlans}")
    private String allPlanApiEndPoint;

    @Value("${service.subscription.api.endpoint.allItems}")
    private String allItemApiEndPoint;

    @Value("${service.subscription.api.endpoint.allOffers}")
    private String allOfferApiEndPoint;

    @Value("${service.subscription.api.endpoint.allPartners}")
    private String allPartnerApiEndPoint;

    @Value("${service.subscription.api.endpoint.subscribePlan}")
    private String subscribePlanEndPoint;

    @Value("${service.subscription.api.endpoint.trialPlanComputation}")
    private String trialPlanComputeEndPoint;

    @Value("${service.subscription.api.endpoint.unSubscribePlan}")
    private String unSubscribePlanEndPoint;

    @Autowired
    @Qualifier(SUBSCRIPTION_SERVICE_S2S_TEMPLATE)
    private RestTemplate restTemplate;
    @Autowired
    private ISqsManagerService sqsMessagePublisher;
    @Autowired
    private WynkApplicationContext myApplicationContext;
    @Autowired
    private PaymentCachingService paymentCachingService;

    @Override
    public List<PlanDTO> getPlans() {
        RequestEntity<Void> allPlanRequest = ChecksumUtils.buildEntityWithAuthHeaders(allPlanApiEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), null, HttpMethod.GET);
        return Objects.requireNonNull(restTemplate.exchange(allPlanRequest, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<AllPlansResponse>>() {
        }).getBody()).getData().getPlans();
    }

    @Override
    public List<PartnerDTO> getPartners() {
        RequestEntity<Void> allPlanRequest = ChecksumUtils.buildEntityWithAuthHeaders(allPartnerApiEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), null, HttpMethod.GET);
        return Objects.requireNonNull(restTemplate.exchange(allPlanRequest, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<Map<String, Collection<PartnerDTO>>>>() {
        }).getBody()).getData().get("allPartners").stream().collect(Collectors.toList());
    }

    @Override
    public List<OfferDTO> getOffers() {
        RequestEntity<Void> allPlanRequest = ChecksumUtils.buildEntityWithAuthHeaders(allOfferApiEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), null, HttpMethod.GET);
        return Objects.requireNonNull(restTemplate.exchange(allPlanRequest, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<Map<String, Collection<OfferDTO>>>>() {
        }).getBody()).getData().get("allOffers").stream().collect(Collectors.toList());
    }


    @Override
    public Collection<ItemDTO> getItems() {
        RequestEntity<Void> allItemRequest = ChecksumUtils.buildEntityWithAuthHeaders(allItemApiEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), null, HttpMethod.GET);
        return Objects.requireNonNull(restTemplate.exchange(allItemRequest, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<AllItemsResponse>>() {
        }).getBody()).getData().getItems();
    }

    @Override
    public void subscribePlanAsync(SubscribePlanAsyncRequest request) {
        try {
            this.publishAsync(SubscriptionProvisioningMessage.builder().uid(request.getUid()).msisdn(request.getMsisdn()).planId(getUpdatedPlanId(request.getPlanId(), request.getPaymentEvent())).paymentCode(request.getPaymentCode()).paymentPartner(BaseConstants.WYNK.toLowerCase()).referenceId(request.getTransactionId()).paymentEvent(request.getPaymentEvent()).transactionStatus(request.getTransactionStatus()).build());
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e);
        }
    }

    @Override
    public void unSubscribePlanAsync(UnSubscribePlanAsyncRequest request) {
        this.publishAsync(SubscriptionProvisioningMessage.builder().uid(request.getUid()).msisdn(request.getMsisdn()).referenceId(request.getTransactionId()).transactionStatus(request.getTransactionStatus()).paymentEvent(PaymentEvent.UNSUBSCRIBE).planId(getUpdatedPlanId(request.getPlanId(), request.getPaymentEvent())).paymentPartner(BaseConstants.WYNK.toLowerCase()).build());
    }

    @Override
    public TrialPlanComputationResponse compute(TrialPlanEligibilityRequest request) {
        try {
            final IAppDetails appDetails = request.getAppDetails();
            final IUserDetails userDetails = request.getUserDetails();
            TrialPlansComputationRequest trialPlansComputationRequest = TrialPlansComputationRequest.builder().planIds(Collections.singletonList(request.getPlanId())).msisdn(userDetails.getMsisdn()).uid(MsisdnUtils.getUidFromMsisdn(userDetails.getMsisdn())).service(request.getService()).appId(appDetails.getApp().getId()).appVersion(appDetails.getAppVersion()).os(appDetails.getOs().getId()).buildNo(appDetails.getBuildNo()).deviceId(appDetails.getDeviceId()).deviceType(appDetails.getDeviceType()).createdTimestamp(System.currentTimeMillis()).build();
            RequestEntity<TrialPlansComputationRequest> requestEntity = ChecksumUtils.buildEntityWithAuthHeaders(trialPlanComputeEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), trialPlansComputationRequest, HttpMethod.POST);
            ResponseEntity<WynkResponse.WynkResponseWrapper<TrialPlanComputationResponse>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<TrialPlanComputationResponse>>() {
            });
            return response.getBody().getData();
        } catch (HttpStatusCodeException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e, e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.PAYMENT_ERROR, "Error occurred while subscribing {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e);
        }
    }

    @Override
    public void subscribePlanSync(SubscribePlanSyncRequest request) {
        try {
            PlanProvisioningRequest planProvisioningRequest = SinglePlanProvisionRequest.builder().uid(request.getUid()).msisdn(request.getMsisdn()).paymentCode(request.getPaymentCode()).referenceId(request.getTransactionId()).planId(getUpdatedPlanId(request.getPlanId(), request.getPaymentEvent())).paymentPartner(BaseConstants.WYNK.toLowerCase()).eventType(request.getPaymentEvent()).build();
            RequestEntity<PlanProvisioningRequest> requestEntity = ChecksumUtils.buildEntityWithAuthHeaders(subscribePlanEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), planProvisioningRequest, HttpMethod.POST);
            ResponseEntity<WynkResponse.WynkResponseWrapper<PlanProvisioningResponse>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<PlanProvisioningResponse>>() {
            });
            if (Objects.nonNull(response.getBody()) && Objects.nonNull(response.getBody().getData())) {
                PlanProvisioningResponse provisioningResponse = response.getBody().getData();
                //TODO: remove deferred state check post IAP fixes.
                if (provisioningResponse.getState() != ProvisionState.SUBSCRIBED && provisioningResponse.getState() != ProvisionState.DEFERRED) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY013);
                }
            }
        } catch (HttpStatusCodeException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e, e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.PAYMENT_ERROR, "Error occurred while subscribing {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e);
        }
    }

    @Override
    public void unSubscribePlanSync(UnSubscribePlanSyncRequest request) {
        try {
            PlanUnProvisioningRequest unProvisioningRequest = PlanUnProvisioningRequest.builder().uid(request.getUid()).referenceId(request.getTransactionId()).planId(getUpdatedPlanId(request.getPlanId(), request.getPaymentEvent())).paymentPartner(BaseConstants.WYNK.toLowerCase()).build();
            RequestEntity<PlanUnProvisioningRequest> requestEntity = ChecksumUtils.buildEntityWithAuthHeaders(unSubscribePlanEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), unProvisioningRequest, HttpMethod.POST);
            ResponseEntity<WynkResponse.WynkResponseWrapper<PlanProvisioningResponse>> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<PlanProvisioningResponse>>() {
            });
            if (Objects.nonNull(response.getBody()) && Objects.nonNull(response.getBody().getData())) {
                PlanProvisioningResponse provisioningResponse = response.getBody().getData();
                if (provisioningResponse.getState() != ProvisionState.UNSUBSCRIBED) {
                    throw new WynkRuntimeException(PaymentErrorType.PAY016);
                }
            }
        } catch (HttpStatusCodeException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY016, e, e.getResponseBodyAsString());
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY016, e, "Unable to unSubscribe plan from payment service");
        }
    }

    private void publishAsync(SubscriptionProvisioningMessage message) {
        try {
            sqsMessagePublisher.publishSQSMessage(message);
        } catch (Exception e) {
            throw new WynkRuntimeException(QueueErrorType.SQS001, e);
        }
    }

    private int getUpdatedPlanId(int planId, PaymentEvent paymentEvent) {
        return paymentEvent == PaymentEvent.TRIAL_SUBSCRIPTION ? paymentCachingService.getPlan(planId).getLinkedFreePlanId() : planId;
    }

}
