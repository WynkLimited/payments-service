package in.wynk.payment.service;

import in.wynk.payment.dto.request.*;
import in.wynk.subscription.common.dto.ItemDTO;
import in.wynk.subscription.common.dto.OfferDTO;
import in.wynk.subscription.common.dto.PartnerDTO;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.response.TrialPlanComputationResponse;

import java.util.Collection;
import java.util.List;

public interface ISubscriptionServiceManager {

    default void subscribePlan(AbstractSubscribePlanRequest request) {
        if(SubscribePlanSyncRequest.class.isAssignableFrom(request.getClass())) {
            subscribePlanSync((SubscribePlanSyncRequest) request);
        } else {
            subscribePlanAsync((SubscribePlanAsyncRequest) request);
        }
    }

    boolean renewalPlanEligibility(int planId, String transactionId, String uid);

    default void unSubscribePlan(AbstractUnSubscribePlanRequest request) {
        if(UnSubscribePlanSyncRequest.class.isAssignableFrom(request.getClass())) {
            unSubscribePlanSync((UnSubscribePlanSyncRequest) request);
        } else {
            unSubscribePlanAsync((UnSubscribePlanAsyncRequest) request);
        }
    }

    TrialPlanComputationResponse compute(TrialPlanEligibilityRequest request);

    void subscribePlanSync(SubscribePlanSyncRequest request);

    void subscribePlanAsync(SubscribePlanAsyncRequest request);

    void unSubscribePlanSync(UnSubscribePlanSyncRequest request);

    void unSubscribePlanAsync(UnSubscribePlanAsyncRequest request);

    Collection<ItemDTO> getItems();

    List<PlanDTO> getPlans();

    List<OfferDTO> getOffers();

    List<PartnerDTO> getPartners();

}