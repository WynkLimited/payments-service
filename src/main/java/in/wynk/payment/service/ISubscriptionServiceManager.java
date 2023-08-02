package in.wynk.payment.service;

import in.wynk.payment.dto.SubscriptionStatus;
import in.wynk.payment.dto.request.*;
import in.wynk.subscription.common.dto.*;
import in.wynk.subscription.common.response.SelectivePlansComputationResponse;

import java.util.Collection;
import java.util.List;

public interface ISubscriptionServiceManager {

    default void subscribePlan(AbstractSubscribePlanRequest request) {
        if (SubscribePlanSyncRequest.class.isAssignableFrom(request.getClass())) {
            subscribePlanSync((SubscribePlanSyncRequest) request);
        } else {
            subscribePlanAsync((SubscribePlanAsyncRequest) request);
        }
    }

    boolean renewalPlanEligibility(int planId, String transactionId, String uid);

    default void unSubscribePlan(AbstractUnSubscribePlanRequest request) {
        if (UnSubscribePlanSyncRequest.class.isAssignableFrom(request.getClass())) {
            unSubscribePlanSync((UnSubscribePlanSyncRequest) request);
        } else {
            unSubscribePlanAsync((UnSubscribePlanAsyncRequest) request);
        }
    }

    SelectivePlansComputationResponse compute(SelectivePlanEligibilityRequest request);

    void subscribePlanSync(SubscribePlanSyncRequest request);

    void subscribePlanAsync(SubscribePlanAsyncRequest request);

    void unSubscribePlanSync(UnSubscribePlanSyncRequest request);

    void unSubscribePlanAsync(UnSubscribePlanAsyncRequest request);

    Collection<ItemDTO> getItems();

    List<PlanDTO> getPlans();

    List<OfferDTO> getOffers();

    List<PartnerDTO> getPartners();

    List<ProductDTO> getProducts();

    List<SubscriptionStatus> getSubscriptionStatus(String uid, String service);

}