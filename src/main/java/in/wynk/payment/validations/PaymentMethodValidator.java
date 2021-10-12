package in.wynk.payment.validations;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.validations.BaseHandler;
import in.wynk.eligibility.dto.AbstractEligibilityEvaluation;
import in.wynk.eligibility.dto.EligibilityResult;
import in.wynk.eligibility.service.AbstractEligibilityService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.eligibility.evaluation.PaymentMethodsItemEligibilityEvaluation;
import in.wynk.payment.eligibility.evaluation.PaymentMethodsPlanEligibilityEvaluation;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsItemEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
import in.wynk.payment.service.impl.PaymentMethodCachingService;

import static in.wynk.common.constant.BaseConstants.PLAN;
import static in.wynk.payment.core.constant.PaymentErrorType.PAY601;

public class PaymentMethodValidator<T extends IPaymentMethodValidatorRequest> extends BaseHandler<T> {
    @Override
    public void handle(T request) {
        PaymentMethod paymentMethod = BeanLocatorFactory.getBean(PaymentMethodCachingService.class).get(request.getPaymentId());
        PaymentOptionsEligibilityRequest paymentOptionsEligibilityRequest = (request.getProductDetails().getType() == PLAN ?
                PaymentOptionsPlanEligibilityRequest.builder().planId(request.getProductDetails().getId()) :
                PaymentOptionsItemEligibilityRequest.builder().itemId(request.getProductDetails().getId()))
                .countryCode(request.getCountryCode())
                .couponCode(request.getCouponCode())
                .service(request.getService())
                .buildNo(request.getBuildNo())
                .msisdn(request.getMsisdn())
                .appId(request.getAppId())
                .os(request.getOs())
                .build();
        AbstractEligibilityEvaluation<PaymentMethod, PaymentOptionsEligibilityRequest> abstractEligibilityEvaluation = (request.getProductDetails().getType() == PLAN ?
                PaymentMethodsPlanEligibilityEvaluation.builder() :
                PaymentMethodsItemEligibilityEvaluation.builder())
                .root(paymentOptionsEligibilityRequest)
                .entity(paymentMethod)
                .build();
        EligibilityResult<PaymentMethod> eligibilityResult = BeanLocatorFactory.getBean(AbstractEligibilityService.class).evaluate(abstractEligibilityEvaluation);
        if (!eligibilityResult.isEligible()) throw new WynkRuntimeException(PAY601);
        super.handle(request);
    }
}