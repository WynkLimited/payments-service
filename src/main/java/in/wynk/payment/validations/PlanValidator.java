package in.wynk.payment.validations;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.validations.BaseHandler;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.request.TrialPlanEligibilityRequest;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.response.TrialPlanComputationResponse;

import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.PLAN;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY602;

public class PlanValidator<T extends IPlanValidatorRequest> extends BaseHandler<T> {
    @Override
    public void handle(T request) {
        if (request.getProductDetails().getType().equalsIgnoreCase(PLAN)) {
            final PlanDTO planDTO = BeanLocatorFactory.getBean(PaymentCachingService.class).getPlan(request.getProductDetails().getId());
            final int planId = request.isTrialOpted() ? planDTO.getLinkedFreePlanId() : planDTO.getId();
            final TrialPlanComputationResponse trialEligibilityResponse = BeanLocatorFactory.getBean(ISubscriptionServiceManager.class).compute(TrialPlanEligibilityRequest.builder().planId(planId).service(planDTO.getService()).appDetails(request.getAppDetails()).userDetails(request.getUserDetails()).build());
            if (Objects.isNull(trialEligibilityResponse) || !trialEligibilityResponse.getEligiblePlans().contains(planId)) {
                throw new WynkRuntimeException(PAY602);
            }
        }
        super.handle(request);
    }
}