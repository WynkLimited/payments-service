package in.wynk.payment.validations;

import in.wynk.common.validations.BaseHandler;

public class PaymentMethodValidator<T extends IPaymentMethodValidatorRequest> extends BaseHandler<T> {
    @Override
    public void handle(T request) {
        super.handle(request);
    }
}