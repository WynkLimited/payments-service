package in.wynk.payment.validations;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.validations.BaseHandler;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.dto.request.SelectivePlanEligibilityRequest;
import in.wynk.payment.service.ISubscriptionServiceManager;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.subscription.common.dto.PlanDTO;
import in.wynk.subscription.common.response.SelectivePlansComputationResponse;

import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.PLAN;
import static in.wynk.payment.core.constant.PaymentErrorType.*;
import static in.wynk.subscription.common.enums.PlanType.FREE;

public class PlanValidator<T extends IPlanValidatorRequest> extends BaseHandler<T> {
    @Override
    public void handle(T request) {
        if (request.getProductDetails().getType().equalsIgnoreCase(PLAN)) {
            final PlanDTO planDTO = BeanLocatorFactory.getBean(PaymentCachingService.class).getPlan(request.getProductDetails().getId());
            final int planId = request.isTrialOpted() ? planDTO.getLinkedFreePlanId() : planDTO.getId();
            if (planDTO.getPlanType() == FREE) throw new WynkRuntimeException(PAY605);
            final SelectivePlansComputationResponse selectivePlansComputationResponse = BeanLocatorFactory.getBean(ISubscriptionServiceManager.class).compute(SelectivePlanEligibilityRequest.builder().planId(planId).service(planDTO.getService()).appDetails(request.getAppDetails()).userDetails(request.getUserDetails()).build());
            if(Objects.nonNull(selectivePlansComputationResponse)) {
                if (request.isTrialOpted() && !request.isAutoRenewOpted()) throw new WynkRuntimeException(PAY603);
                if(request.getPaymentDetails().isMandate() && !(selectivePlansComputationResponse.getEligiblePlans().contains(planId) || selectivePlansComputationResponse.getActivePlans().contains(planId))) throw new WynkRuntimeException(PAY604);
                if(!request.getPaymentDetails().isMandate() && (!selectivePlansComputationResponse.getEligiblePlans().contains(planId) && (request.isTrialOpted() || !selectivePlansComputationResponse.getActivePlans().contains(planId)))) throw new WynkRuntimeException(PAY605);
            } else {
                throw new WynkRuntimeException(PAY606);
            }
        }
        super.handle(request);
    }
}