package in.wynk.payment.validations;

import in.wynk.common.validations.BaseHandler;

public class PlanValidator<T extends IPlanValidatorRequest> extends BaseHandler<T> {
    @Override
    public void handle(T request) {
        super.handle(request);
    }
}