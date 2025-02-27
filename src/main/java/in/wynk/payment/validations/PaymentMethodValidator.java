package in.wynk.payment.validations;

import in.wynk.common.utils.BeanLocatorFactory;
import in.wynk.common.validations.BaseHandler;
import in.wynk.eligibility.dto.AbstractEligibilityEvaluation;
import in.wynk.eligibility.dto.EligibilityResult;
import in.wynk.eligibility.service.AbstractEligibilityService;
import in.wynk.exception.WynkRuntimeException;
import in.wynk.payment.constant.CardConstants;
import in.wynk.payment.core.dao.entity.IPaymentDetails;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.core.service.PaymentMethodCachingService;
import in.wynk.payment.dto.request.charge.card.CardPaymentDetails;
import in.wynk.payment.eligibility.evaluation.PaymentMethodsItemEligibilityEvaluation;
import in.wynk.payment.eligibility.evaluation.PaymentMethodsPlanEligibilityEvaluation;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsItemEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;

import java.util.Objects;

import static in.wynk.common.constant.BaseConstants.PLAN;
import static in.wynk.payment.core.constant.PaymentErrorType.*;

public class PaymentMethodValidator<T extends IPaymentMethodValidatorRequest> extends BaseHandler<T> {
    @Override
    public void handle(T request) {
        PaymentMethod paymentMethod = BeanLocatorFactory.getBean(PaymentMethodCachingService.class).get(request.getPaymentId());
        if(CardConstants.CARD.equals(paymentMethod.getGroup())) {
            IPaymentDetails paymentDetails = request.getPaymentDetails();
            final CardPaymentDetails cardPaymentDetails = (CardPaymentDetails) paymentDetails;
            if(CardConstants.SAVED_CARD_TYPE.equals(cardPaymentDetails.getCardDetails().getType()) && (paymentDetails.isAutoRenew() || paymentDetails.isTrialOpted() || paymentDetails.isMandate())) throw new WynkRuntimeException(PAY609);
        }
        PaymentOptionsEligibilityRequest paymentOptionsEligibilityRequest = (Objects.equals(request.getProductDetails().getType(), PLAN) ?
                PaymentOptionsPlanEligibilityRequest.builder().planId(request.getProductDetails().getId()) :
                PaymentOptionsItemEligibilityRequest.builder().itemId(request.getProductDetails().getId()))
                .client(request.getClientDetails().getAlias())
                .countryCode(request.getCountryCode())
                .couponCode(request.getCouponCode())
                .service(request.getService())
                .buildNo(request.getBuildNo())
                .msisdn(request.getMsisdn())
                .appId(request.getAppId())
                .os(request.getOs())
                .si(request.getSi())
                .build();
        AbstractEligibilityEvaluation<PaymentMethod, PaymentOptionsEligibilityRequest> abstractEligibilityEvaluation = (Objects.equals(request.getProductDetails().getType(), PLAN) ?
                PaymentMethodsPlanEligibilityEvaluation.builder() :
                PaymentMethodsItemEligibilityEvaluation.builder())
                .root(paymentOptionsEligibilityRequest)
                .entity(paymentMethod)
                .build();
        EligibilityResult<PaymentMethod> eligibilityResult = BeanLocatorFactory.getBean(AbstractEligibilityService.class).evaluate(abstractEligibilityEvaluation);
       if (!eligibilityResult.isEligible()) throw new WynkRuntimeException(PAY601);
        if((request.getPaymentDetails().isMandate() || request.getPaymentDetails().isTrialOpted()) && !paymentMethod.isAutoRenewSupported()) throw new WynkRuntimeException(PAY603);
        if(request.getPaymentDetails().isMandate() && request.getPaymentDetails().isTrialOpted()) throw new WynkRuntimeException(PAY608);
       super.handle(request);
    }
}