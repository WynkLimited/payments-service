package in.wynk.payment.service;

import in.wynk.common.dto.WynkResponse;
import in.wynk.common.enums.PaymentEvent;
import in.wynk.payment.dto.BestValuePlanPurchaseRequest;
import in.wynk.payment.dto.BestValuePlanResponse;
import in.wynk.payment.dto.SubscriptionStatus;
import in.wynk.payment.dto.request.*;
import in.wynk.subscription.common.dto.*;
import in.wynk.subscription.common.request.UserPersonalisedPlanRequest;
import in.wynk.subscription.common.response.SelectivePlansComputationResponse;
import org.springframework.http.ResponseEntity;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface ISubscriptionServiceManager {

    default void subscribePlan (AbstractSubscribePlanRequest request) {
        if (SubscribePlanSyncRequest.class.isAssignableFrom(request.getClass())) {
            subscribePlanSync((SubscribePlanSyncRequest) request);
        } else {
            validateAndSubscribePlanAsync((SubscribePlanAsyncRequest) request);
        }
    }

    ResponseEntity<WynkResponse.WynkResponseWrapper<RenewalPlanEligibilityResponse>> renewalPlanEligibilityResponse (int planId, String uid);

    boolean isDeferred (String paymentMethod, long furtherDefer, boolean isPreDebitFlow);

    default void unSubscribePlan (AbstractUnSubscribePlanRequest request) {
        if (UnSubscribePlanSyncRequest.class.isAssignableFrom(request.getClass())) {
            unSubscribePlanSync((UnSubscribePlanSyncRequest) request);
        } else {
            unSubscribePlanAsync((UnSubscribePlanAsyncRequest) request);
        }
    }

    SelectivePlansComputationResponse compute (SelectivePlanEligibilityRequest request);

    void subscribePlanSync (SubscribePlanSyncRequest request);

    void validateAndSubscribePlanAsync(SubscribePlanAsyncRequest request);

    void unSubscribePlanSync (UnSubscribePlanSyncRequest request);

    void unSubscribePlanAsync (UnSubscribePlanAsyncRequest request);

    Collection<ItemDTO> getItems ();

    List<PlanDTO> getPlans ();

    List<OfferDTO> getOffers ();

    List<PartnerDTO> getPartners ();

    List<ProductDTO> getProducts ();

    PlanDTO getUserPersonalisedPlanOrDefault (UserPersonalisedPlanRequest request, PlanDTO defaultPlan);

    List<SubscriptionStatus> getSubscriptionStatus (String uid, String service);

    Integer getUpdatedPlanId (Integer planId, PaymentEvent paymentEvent);

    BestValuePlanResponse getBestValuePlan (BestValuePlanPurchaseRequest request, Map<String, String> requestParam);

    ThanksPlanResponse getThanksPlanForAdditiveDays(String msisdn);
}