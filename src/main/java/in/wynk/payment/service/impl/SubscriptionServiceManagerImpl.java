package in.wynk.payment.service.impl;

import static in.wynk.payment.core.constant.BeanConstant.SUBSCRIPTION_SERVICE_S2S_TEMPLATE;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY105;

import com.github.annotation.analytic.core.service.AnalyticService;
import in.wynk.cache.aspect.advice.CacheEvict;
import in.wynk.cache.aspect.advice.CachePut;
import in.wynk.cache.aspect.advice.Cacheable;
import in.wynk.cache.constant.BeanConstant;
import in.wynk.common.constant.BaseConstants;
import in.wynk.common.context.WynkApplicationContext;
import in.wynk.common.dto.WynkResponse;
import in.wynk.common.dto.WynkResponse.WynkResponseWrapper;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.utils.ChecksumUtils;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.identity.client.utils.IdentityUtils;
import in.wynk.payment.constant.Validity;
import in.wynk.payment.core.constant.PaymentConstants;
import in.wynk.payment.core.constant.PaymentErrorType;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.IAppDetails;
import in.wynk.payment.core.dao.entity.IUserDetails;
import in.wynk.payment.dto.BestValuePlanPurchaseRequest;
import in.wynk.payment.dto.BestValuePlanResponse;
import in.wynk.payment.dto.SubscriptionStatus;
import in.wynk.payment.dto.aps.common.ApsConstant;
import in.wynk.payment.dto.request.AbstractSubscribePlanRequest;
import in.wynk.payment.dto.request.SelectivePlanEligibilityRequest;
import in.wynk.payment.dto.request.SubscribePlanAsyncRequest;
import in.wynk.payment.dto.request.SubscribePlanSyncRequest;
import in.wynk.payment.dto.request.UnSubscribePlanAsyncRequest;
import in.wynk.payment.dto.request.UnSubscribePlanSyncRequest;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.payment.utils.CurrencyCountryUtils;
import in.wynk.queue.constant.QueueErrorType;
import in.wynk.stream.producer.IKafkaPublisherService;
import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.dto.ProductDTO;
import in.wynk.subscription.common.dto.RenewalPlanEligibilityRequest;
import in.wynk.subscription.common.dto.RenewalPlanEligibilityResponse;
import in.wynk.subscription.common.dto.ThanksPlanResponse;
import in.wynk.subscription.common.enums.ProvisionState;
import in.wynk.subscription.common.message.SubscriptionProvisioningMessage;
import in.wynk.subscription.common.request.PlanProvisioningRequest;
import in.wynk.subscription.common.request.PlanUnProvisioningRequest;
import in.wynk.subscription.common.request.SelectivePlansComputationRequest;
import in.wynk.subscription.common.request.SessionRequest;
import in.wynk.subscription.common.request.SinglePlanAdditiveProvisionRequest;
import in.wynk.subscription.common.request.UserPersonalisedPlanRequest;
import in.wynk.subscription.common.response.AllItemsResponse;
import in.wynk.subscription.common.response.AllPlansResponse;
import in.wynk.subscription.common.response.PlanProvisioningResponse;
import in.wynk.subscription.common.response.SelectivePlansComputationResponse;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import static in.wynk.payment.core.constant.BeanConstant.AIRTEL_PAY_STACK;

@Service
@Slf4j
public class SubscriptionServiceManagerImpl implements ISubscriptionServiceManager {

    @Value("${payment.recurring.offset.hour}")
    private int hour;

    @Value("${service.subscription.api.endpoint.allProducts}")
    private String allProductApiEndPoint;

    @Value("${service.subscription.api.endpoint.personalisedPlan}")
    private String personalisedPlanApiEndPoint;

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

    @Value("${service.subscription.api.endpoint.subscribePlanAdditive}")
    private String subscribePlanAdditiveEndPoint;

    @Value("${service.subscription.api.endpoint.selectivePlanComputation}")
    private String selectivePlanComputeEndPoint;

    @Value("${service.subscription.api.endpoint.selectivePlanComputationV2}")
    private String selectivePlanComputeEndPointV2;

    @Value("${service.subscription.api.endpoint.unSubscribePlan}")
    private String unSubscribePlanEndPoint;

    @Value("${service.subscription.api.endpoint.renewalPlanEligibility}")
    private String renewalPlanEligibilityEndpoint;

    @Value("${service.subscription.api.endpoint.status}")
    private String subscriptionStatusEndpoint;

    @Value("${service.subscription.api.endpoint.bestValue}")
    private String subscriptionBestValueEndpoint;

    @Value("${service.subscription.api.endpoint.thanksPlan}")
    private String thanksPlanEndPoint;

    @Autowired
    @Qualifier(SUBSCRIPTION_SERVICE_S2S_TEMPLATE)
    private RestTemplate restTemplate;

    @Autowired
    private IKafkaPublisherService kafkaPublisherService;

    @Autowired
    private WynkApplicationContext myApplicationContext;

    @Lazy
    @Autowired
    private PaymentCachingService cachingService;

    @Override
    public List<PlanDTO> getPlans () {
        RequestEntity<Void> allPlanRequest =
                ChecksumUtils.buildEntityWithAuthHeaders(allPlanApiEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), null, HttpMethod.GET);
        return Objects.requireNonNull(restTemplate.exchange(allPlanRequest, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<AllPlansResponse>>() {
        }).getBody()).getData().getPlans();
    }

    @Override
    public List<PartnerDTO> getPartners () {
        RequestEntity<Void> allPlanRequest =
                ChecksumUtils.buildEntityWithAuthHeaders(allPartnerApiEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), null, HttpMethod.GET);
        return Objects.requireNonNull(restTemplate.exchange(allPlanRequest, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<Map<String, Collection<PartnerDTO>>>>() {
        }).getBody()).getData().get("allPartners").stream().collect(Collectors.toList());
    }

    @Override
    public List<OfferDTO> getOffers () {
        RequestEntity<Void> allPlanRequest =
                ChecksumUtils.buildEntityWithAuthHeaders(allOfferApiEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), null, HttpMethod.GET);
        return Objects.requireNonNull(restTemplate.exchange(allPlanRequest, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<Map<String, Collection<OfferDTO>>>>() {
        }).getBody()).getData().get("allOffers").stream().collect(Collectors.toList());
    }


    @Override
    public Collection<ItemDTO> getItems () {
        RequestEntity<Void> allItemRequest =
                ChecksumUtils.buildEntityWithAuthHeaders(allItemApiEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), null, HttpMethod.GET);
        return Objects.requireNonNull(restTemplate.exchange(allItemRequest, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<AllItemsResponse>>() {
        }).getBody()).getData().getItems();
    }

    @Override
    public List<ProductDTO> getProducts () {
        RequestEntity<Void> allProductRequest =
                ChecksumUtils.buildEntityWithAuthHeaders(allProductApiEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), null, HttpMethod.GET);
        return Objects.requireNonNull(restTemplate.exchange(allProductRequest, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<Map<String, Collection<ProductDTO>>>>() {
        }).getBody()).getData().get("allProducts").stream().collect(Collectors.toList());
    }

    public List<SubscriptionStatus> getSubscriptionStatus (String uid, String service) {
        final URI uri = restTemplate.getUriTemplateHandler().expand(subscriptionStatusEndpoint, uid, service);
        RequestEntity<Void> subscriptionStatusRequest =
                ChecksumUtils.buildEntityWithAuthHeaders(uri.toString(), myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), null, HttpMethod.GET);
        return restTemplate.exchange(subscriptionStatusRequest, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<List<SubscriptionStatus>>>() {
        }).getBody().getData();
    }


    @Override
    public PlanDTO getUserPersonalisedPlanOrDefault (UserPersonalisedPlanRequest request, PlanDTO defaultPlan) {
        if (!defaultPlan.isPersonalize()) {
            return defaultPlan;
        }
        try {
            RequestEntity<UserPersonalisedPlanRequest> requestEntity =
                    ChecksumUtils.buildEntityWithAuthHeaders(personalisedPlanApiEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), request, HttpMethod.POST);
            return Objects.requireNonNull(restTemplate.exchange(requestEntity, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<PlanDTO>>() {
            }).getBody()).getData();
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.EXTERNAL_SERVICE_FAILURE, "Exception occurred while getting user personalised plan. Exception: {}", e.getMessage(), e);
            return defaultPlan;
        }
    }


    @Override
    public ResponseEntity<WynkResponse.WynkResponseWrapper<RenewalPlanEligibilityResponse>> renewalPlanEligibilityResponse (int planId, String uid) {
        try {
            RenewalPlanEligibilityRequest renewalPlanEligibilityRequest =
                    RenewalPlanEligibilityRequest.builder().uid(uid).planId(planId).countryCode(CurrencyCountryUtils.findCountryCodeByPlanId(planId)).build();
            RequestEntity<RenewalPlanEligibilityRequest> requestEntity =
                    ChecksumUtils.buildEntityWithAuthHeaders(renewalPlanEligibilityEndpoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), renewalPlanEligibilityRequest,
                            HttpMethod.POST);
            ResponseEntity<WynkResponse.WynkResponseWrapper<RenewalPlanEligibilityResponse>> response =
                    restTemplate.exchange(requestEntity, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<RenewalPlanEligibilityResponse>>() {
                    });
            return response;
        } catch (Exception e) {
            throw new WynkRuntimeException(PAY105);
        }
    }

    public boolean isDeferred (String paymentMethod, long furtherDefer, boolean isPreDebitFlow) {
        long oneHourWindow = (long) hour * 60 * 60 * 1000;
        long twoDayPlusOneHourWindow = ((long) 2 * 24 * 60 * 60 * 1000) + oneHourWindow;
        return (Objects.equals(paymentMethod, AIRTEL_PAY_STACK) || isPreDebitFlow) ? (furtherDefer > twoDayPlusOneHourWindow) : (furtherDefer > oneHourWindow);
    }

    @Override
    public void validateAndSubscribePlanAsync(SubscribePlanAsyncRequest request) {
        if (isExternallyProvisionablePlan(request.getPlanId()) &&
            (request.getPaymentGateway().getId().equalsIgnoreCase(PaymentConstants.ADD_TO_BILL) || request.getPaymentGateway().getId().equalsIgnoreCase(ApsConstant.APS_V2))) {
            log.info("plan {} has to be provision externally for uid {}, stopping subscribePlanAsync flow", request.getPlanId(), request.getUid());
            additiveDaysSubscribe(request);
            return;
        }
        try {
            this.publishAsync(SubscriptionProvisioningMessage.builder()
                                                             .uid(request.getUid())
                                                             .msisdn(request.getMsisdn())
                                                             .subscriberId(request.getSubscriberId())
                                                             .planId(getUpdatedPlanId(request.getPlanId(), request.getPaymentEvent()))
                                                             .paymentCode(request.getPaymentGateway().getCode())
                                                             .paymentPartner(BaseConstants.WYNK.toLowerCase())
                                                             .referenceId(request.getTransactionId())
                                                             .paymentEvent(request.getPaymentEvent())
                                                             .transactionStatus(request.getTransactionStatus())
                                                             .externalActivationNotRequired(request.getPaymentGateway().isExternalActivationNotRequired())
                                                             .os(request.getTriggerDataRequest().getOs())
                                                             .appVersion(request.getTriggerDataRequest().getAppVersion())
                                                             .build());
        } catch (Exception e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e);
        }
    }

    @Override
    public void unSubscribePlanAsync(UnSubscribePlanAsyncRequest request) {
        this.publishAsync(
                SubscriptionProvisioningMessage.builder().uid(request.getUid()).msisdn(request.getMsisdn()).referenceId(request.getTransactionId()).transactionStatus(request.getTransactionStatus())
                        .paymentEvent(request.getPaymentEvent()).planId(getUpdatedPlanId(request.getPlanId(), request.getPaymentEvent())).paymentPartner(BaseConstants.WYNK.toLowerCase())
                        .appVersion(request.getTriggerDataRequest().getAppVersion()).os(request.getTriggerDataRequest().getOs()).build());
    }

    @Override
    public SelectivePlansComputationResponse compute(SelectivePlanEligibilityRequest request) {
        try {
            final IAppDetails appDetails = request.getAppDetails();
            final IUserDetails userDetails = request.getUserDetails();
            final SelectivePlansComputationRequest selectivePlansComputationRequest =
                SelectivePlansComputationRequest.builder().planIds(Collections.singletonList(request.getPlanId())).msisdn(userDetails.getMsisdn())
                                                .uid(IdentityUtils.getUidFromUserName(userDetails.getMsisdn(), request.getService())).service(request.getService()).appId(appDetails.getAppId())
                                                .appVersion(appDetails.getAppVersion()).os(appDetails.getOs()).buildNo(appDetails.getBuildNo()).deviceId(appDetails.getDeviceId()).deviceType(appDetails.getDeviceType())
                                                .createdTimestamp(System.currentTimeMillis()).countryCode(userDetails.getCountryCode()).build();
            final RequestEntity<SelectivePlansComputationRequest> requestEntity = BeanLocatorFactory.getBean(PaymentCachingService.class).isV2SubscriptionJourney(request.getPlanId()) ?
                ChecksumUtils.buildEntityWithAuthHeaders(selectivePlanComputeEndPointV2, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(),
                                                         selectivePlansComputationRequest, HttpMethod.POST) :
                ChecksumUtils.buildEntityWithAuthHeaders(selectivePlanComputeEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), selectivePlansComputationRequest,
                                                         HttpMethod.POST);
            final ResponseEntity<WynkResponse.WynkResponseWrapper<SelectivePlansComputationResponse>> response =
                restTemplate.exchange(requestEntity, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<SelectivePlansComputationResponse>>() {
                });
            return response.getBody().getData();
        } catch (HttpStatusCodeException e) {
            throw new WynkRuntimeException(PaymentErrorType.PAY107, e, e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.PAYMENT_ERROR, "Error occurred while subscribing {}", e.getMessage(), e);
            throw new WynkRuntimeException(PaymentErrorType.PAY107, e);
        }
    }

    @Override
    public void subscribePlanSync(SubscribePlanSyncRequest request) {
        if (isExternallyProvisionablePlan(request.getPlanId()) &&
            (request.getPaymentGateway().getId().equalsIgnoreCase(PaymentConstants.ADD_TO_BILL) || request.getPaymentGateway().getId().equalsIgnoreCase(ApsConstant.APS_V2))) {
            log.info("plan {} has to be provision externally for uid {}, stopping subscribePlanSync flow", request.getPlanId(), request.getUid());
            additiveDaysSubscribe(request);
            return;
        }
        try {
            PlanProvisioningRequest planProvisioningRequest =
                SinglePlanAdditiveProvisionRequest.builder()
                                                  .uid(request.getUid())
                                                  .msisdn(request.getMsisdn())
                                                  .subscriberId(request.getSubscriberId())
                                                  .paymentCode(request.getPaymentGateway().getCode())
                                                  .referenceId(request.getTransactionId())
                                                  .planId(getUpdatedPlanId(request.getPlanId(), request.getPaymentEvent()))
                                                  .paymentPartner(BaseConstants.WYNK.toLowerCase())
                                                  .eventType(request.getPaymentEvent())
                                                  .externalActivationNotRequired(request.getPaymentGateway().isExternalActivationNotRequired())
                                                  .triggerDataRequest(request.getTriggerDataRequest())
                                                  .build();
            RequestEntity<PlanProvisioningRequest> requestEntity =
                ChecksumUtils.buildEntityWithAuthHeaders(subscribePlanEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), planProvisioningRequest,
                                                         HttpMethod.POST);
            ResponseEntity<WynkResponse.WynkResponseWrapper<PlanProvisioningResponse>> response =
                restTemplate.exchange(requestEntity, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<PlanProvisioningResponse>>() {
                });
            if (Objects.nonNull(response.getBody()) && Objects.nonNull(response.getBody().getData())) {
                PlanProvisioningResponse provisioningResponse = response.getBody().getData();
                //TODO: remove deferred state check post IAP fixes.
                if (provisioningResponse.getState() != ProvisionState.SUBSCRIBED && provisioningResponse.getState() != ProvisionState.DEFERRED) {
                    this.publishAsync(SubscriptionProvisioningMessage.builder().uid(request.getUid()).msisdn(request.getMsisdn()).subscriberId(request.getSubscriberId())
                            .planId(getUpdatedPlanId(request.getPlanId(), request.getPaymentEvent())).paymentCode(request.getPaymentGateway().getCode())
                            .paymentPartner(BaseConstants.WYNK.toLowerCase()).referenceId(request.getTransactionId()).paymentEvent(request.getPaymentEvent())
                            .transactionStatus(request.getTransactionStatus()).externalActivationNotRequired(request.getPaymentGateway().isExternalActivationNotRequired())
                            .os(request.getTriggerDataRequest().getOs()).appVersion(request.getTriggerDataRequest().getAppVersion()).build());
                    throw new WynkRuntimeException(PaymentErrorType.PAY013);
                }
            }
        } catch (HttpStatusCodeException e) {
            this.publishAsync(SubscriptionProvisioningMessage.builder().uid(request.getUid()).msisdn(request.getMsisdn()).subscriberId(request.getSubscriberId())
                    .planId(getUpdatedPlanId(request.getPlanId(), request.getPaymentEvent())).paymentCode(request.getPaymentGateway().getCode()).paymentPartner(BaseConstants.WYNK.toLowerCase())
                    .referenceId(request.getTransactionId()).paymentEvent(request.getPaymentEvent()).transactionStatus(request.getTransactionStatus())
                    .externalActivationNotRequired(request.getPaymentGateway().isExternalActivationNotRequired()).os(request.getTriggerDataRequest().getOs())
                    .appVersion(request.getTriggerDataRequest().getAppVersion()).build());
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e, e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.PAYMENT_ERROR, "Error occurred while subscribing {}", e.getMessage(), e);
            this.publishAsync(SubscriptionProvisioningMessage.builder().uid(request.getUid()).msisdn(request.getMsisdn()).subscriberId(request.getSubscriberId())
                    .planId(getUpdatedPlanId(request.getPlanId(), request.getPaymentEvent())).paymentCode(request.getPaymentGateway().getCode()).paymentPartner(BaseConstants.WYNK.toLowerCase())
                    .referenceId(request.getTransactionId()).paymentEvent(request.getPaymentEvent()).transactionStatus(request.getTransactionStatus())
                    .externalActivationNotRequired(request.getPaymentGateway().isExternalActivationNotRequired()).os(request.getTriggerDataRequest().getOs())
                    .appVersion(request.getTriggerDataRequest().getAppVersion()).build());
            throw new WynkRuntimeException(PaymentErrorType.PAY013, e);
        }
    }

    private void additiveDaysSubscribe(AbstractSubscribePlanRequest request) {
        log.info("AdditiveValidity request: {}", request);
        try {
            if (!ApsConstant.APS_V2.equalsIgnoreCase(request.getPaymentGateway().getId())) {
                return;
            }

            PlanDTO planDTO = cachingService.getPlan(request.getPlanId());
            if (Boolean.FALSE.equals(MapUtils.getBoolean(planDTO.getMeta(), "additiveValidityEnabled", false))) {
                log.info("Additive validity not enabled for plan id: {}", request.getPlanId());
                AnalyticService.update("AdditiveValidity", 0);
                return;
            }

            Integer additiveDays = getAdditiveDays(request.getMsisdn(), request.getPlanId());
            AnalyticService.update("AdditiveValidity", additiveDays);
            PlanProvisioningRequest planProvisioningRequest = SinglePlanAdditiveProvisionRequest.builder()
                                                                                                .uid(request.getUid())
                                                                                                .msisdn(request.getMsisdn())
                                                                                                .subscriberId(request.getSubscriberId())
                                                                                                .paymentCode(request.getPaymentGateway().getCode())
                                                                                                .referenceId(request.getTransactionId())
                                                                                                .planId(getUpdatedPlanId(request.getPlanId(), request.getPaymentEvent()))
                                                                                                .paymentPartner(BaseConstants.WYNK.toLowerCase())
                                                                                                .eventType(request.getPaymentEvent())
                                                                                                .externalActivationNotRequired(request.getPaymentGateway().isExternalActivationNotRequired())
                                                                                                .triggerDataRequest(request.getTriggerDataRequest())
                                                                                                .validityInDays(additiveDays)
                                                                                                .build();
            RequestEntity<PlanProvisioningRequest> requestEntity = ChecksumUtils.buildEntityWithAuthHeaders(subscribePlanAdditiveEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), planProvisioningRequest, HttpMethod.POST);
            log.info("AdditiveValidity requestEntity: {}", requestEntity);
            restTemplate.exchange(requestEntity,
                                  new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<PlanProvisioningResponse>>() {
                                  });

            removeFromCache(request.getMsisdn(), request.getPlanId());
        } catch (Exception e) {
            log.error("Error in Subscribe Plan Additive request", e);
        }
    }

    @CachePut(cacheName = "additiveDays", cacheKey = "#msisdn + ':' + #planId", l2CacheTtl = 3600, cacheManager = BeanConstant.L2CACHE_MANAGER)
    @Override
    public int cacheAdditiveDays(String msisdn, String planId) {
        try {
            log.info("testAsync inside");
            ThanksPlanResponse thanksPlanResponse = getThanksPlanForAdditiveDays(msisdn);
            AnalyticService.update("ActiveThanksPlan", thanksPlanResponse.toString());
            return thanksPlanResponse.getData().getDaysTillExpiry();
        } catch (Exception e) {
            log.error("Error in subscriptionServiceManager.getThanksPlanForAdditiveDays", e);
        }
        return 0;
    }

    @Cacheable(cacheName = "additiveDays", cacheKey = "#msisdn + ':' + #planId", l2CacheTtl = 3600, cacheManager = BeanConstant.L2CACHE_MANAGER)
    public Integer getAdditiveDays(String msisdn, Integer planId) {
        return null;
    }

    @CacheEvict(cacheName = "additiveDays", cacheKey = "#msisdn + ':' + #planId", cacheManager = BeanConstant.L2CACHE_MANAGER)
    public void removeFromCache(String msisdn, Integer planId) {
    }

    @Override
    public void unSubscribePlanSync(UnSubscribePlanSyncRequest request) {
        try {
            PlanUnProvisioningRequest unProvisioningRequest =
                PlanUnProvisioningRequest.builder().msisdn(request.getMsisdn()).uid(request.getUid()).referenceId(request.getTransactionId()).paymentEvent(request.getPaymentEvent())
                                         .planId(getUpdatedPlanId(request.getPlanId(), request.getPaymentEvent())).paymentPartner(BaseConstants.WYNK.toLowerCase())
                            .triggerDataRequest(request.getTriggerDataRequest()).source(myApplicationContext.getClientAlias()).build();
            RequestEntity<PlanUnProvisioningRequest> requestEntity =
                ChecksumUtils.buildEntityWithAuthHeaders(unSubscribePlanEndPoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), unProvisioningRequest,
                                                         HttpMethod.POST);
            ResponseEntity<WynkResponse.WynkResponseWrapper<PlanProvisioningResponse>> response =
                restTemplate.exchange(requestEntity, new ParameterizedTypeReference<WynkResponse.WynkResponseWrapper<PlanProvisioningResponse>>() {
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
           // sqsMessagePublisher.publishSQSMessage(message);
            kafkaPublisherService.publishKafkaMessage(message);
        } catch (Exception e) {
            throw new WynkRuntimeException(QueueErrorType.SQS001, e);
        }
    }

    public Integer getUpdatedPlanId(Integer planId, PaymentEvent paymentEvent) {
        return paymentEvent == PaymentEvent.TRIAL_SUBSCRIPTION ? BeanLocatorFactory.getBean(PaymentCachingService.class).getPlan(planId).getLinkedFreePlanId() : planId;
    }

    private boolean isExternallyProvisionablePlan(int planId) {
        return BeanLocatorFactory.getBean(PaymentCachingService.class).getPlan(planId).isProvisionNotRequired();
    }

    @Override
    public BestValuePlanResponse getBestValuePlan(final BestValuePlanPurchaseRequest request, final Map<String, String> additionalParam) {
        String planId = null;
        try {

            if (request.getProductDetails() != null && StringUtils.isNotEmpty(request.getProductDetails().getId())) {
                AnalyticService.update(BaseConstants.REQUEST_PLAN_ID, request.getProductDetails().getId());
                return BestValuePlanResponse.builder().planId(request.getProductDetails().getId()).bestValuePlanPurchaseRequest(request).build();
            }
            if (!validateMandatoryParam(additionalParam)) {
                AnalyticService.update(BaseConstants.MISSING_MANDATORY_PARAM);
                return BestValuePlanResponse.builder().bestValuePlanPurchaseRequest(request).build();
            }
            final SessionRequest sessionRequestWithAdditionalParam = request.toSessionWithAdditionalParam(additionalParam);
            AnalyticService.update(sessionRequestWithAdditionalParam);
            final RequestEntity<SessionRequest> sessionRequest = ChecksumUtils.buildEntityWithAuthHeaders(subscriptionBestValueEndpoint,
                                                                                                          myApplicationContext.getClientId(),
                                                                                                          myApplicationContext.getClientSecret(),
                                                                                                          sessionRequestWithAdditionalParam,
                                                                                                          HttpMethod.POST);
            final WynkResponseWrapper<in.wynk.common.dto.BestValuePlanResponse> body = restTemplate.exchange(sessionRequest,
                                                                                                             new ParameterizedTypeReference<WynkResponseWrapper<in.wynk.common.dto.BestValuePlanResponse>>() {
                                                                                                             }).getBody();
            if (Objects.nonNull(body) && Objects.nonNull(body.getData()) && StringUtils.isNotEmpty(body.getData().getPlanId())) {
                planId = body.getData().getPlanId();
                AnalyticService.update(BaseConstants.BEST_VALUE_PLAN, planId);
            }
        } catch (Exception e) {
            log.error(PaymentLoggingMarker.BEST_VALUE_PLAN_API_ERROR, e.getMessage());
        }
        return BestValuePlanResponse.builder().bestValuePlanPurchaseRequest(request).planId(planId).build();
    }

    private boolean validateMandatoryParam(final Map<String, String> additionalParam) {
        String pg = additionalParam.get(BaseConstants.DEEPLINK_PACK_GROUP);
        int validity = Validity.getValidity(additionalParam.get(BaseConstants.VALIDITY));
        String price = additionalParam.get(BaseConstants.PRICE);
        return !StringUtils.isEmpty(pg) && (validity != -1 || !StringUtils.isEmpty(price));
    }

    public ThanksPlanResponse getThanksPlanForAdditiveDays(String msisdn) {
        String endpoint = thanksPlanEndPoint + "?msisdn=" + msisdn;
        RequestEntity<Void> requestEntity = ChecksumUtils.buildEntityWithAuthHeaders(endpoint, myApplicationContext.getClientId(), myApplicationContext.getClientSecret(), null, HttpMethod.GET);
        ResponseEntity<ThanksPlanResponse> response = restTemplate.exchange(requestEntity, new ParameterizedTypeReference<ThanksPlanResponse>(){});
        return response.getBody();
    }
}