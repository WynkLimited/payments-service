package in.wynk.payment.eligibility.service;

import in.wynk.eligibility.dto.AbstractEligibilityEvaluation;
import in.wynk.eligibility.dto.EligibilityResult;
import in.wynk.eligibility.service.AbstractEligibilityService;
import in.wynk.payment.core.constant.PaymentLoggingMarker;
import in.wynk.payment.core.dao.entity.PaymentMethod;
import in.wynk.payment.dto.response.PaymentOptionsComputationResponse;
import in.wynk.payment.eligibility.evaluation.PaymentMethodsItemEligibilityEvaluation;
import in.wynk.payment.eligibility.evaluation.PaymentMethodsPlanEligibilityEvaluation;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
import in.wynk.payment.service.PaymentCachingService;
import in.wynk.spel.IRuleEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class PaymentOptionComputationManager<R extends PaymentOptionsComputationResponse, T extends PaymentOptionsEligibilityRequest> extends AbstractEligibilityService<PaymentMethod,T> implements IPaymentOptionComputationManager<R,T>{

    private final IRuleEvaluator evaluator;
    private final PaymentCachingService cachingService;

    public PaymentOptionComputationManager(IRuleEvaluator evaluator,PaymentCachingService cachingService) {
        super(evaluator);
        this.evaluator = evaluator;
        this.cachingService = cachingService;
    }

    @Override
    public PaymentOptionsComputationResponse compute(PaymentOptionsEligibilityRequest request) {
        final String group = request.getGroup();
        final List<PaymentMethod> paymentMethodsInGroup = cachingService.getGroupedPaymentMethods().get(group);
        final List<EligibilityResult<PaymentMethod>> eligibilityResults = paymentMethodsInGroup.stream().map(paymentMethod -> (AbstractEligibilityEvaluation<PaymentMethod, T>)((request instanceof PaymentOptionsPlanEligibilityRequest)?PaymentMethodsPlanEligibilityEvaluation.builder().root((PaymentOptionsPlanEligibilityRequest) request).entity(paymentMethod).build():PaymentMethodsItemEligibilityEvaluation.builder().root(request).entity(paymentMethod).build())).map(this::evaluate).collect(Collectors.toList());
        final Set<PaymentMethod> eligiblePaymentMethods = eligibilityResults.stream().filter(EligibilityResult::isEligible).map(EligibilityResult::getEntity).collect(Collectors.toSet());
        return PaymentOptionsComputationResponse.builder().paymentMethods(eligiblePaymentMethods).build();
    }

    @Override
    protected void onEligibilityResult(final EligibilityResult<PaymentMethod> result) {
        log.debug(PaymentLoggingMarker.PAYMENT_ELIGIBILITY_INFO, "payment method id: {} with eligibility rule: {} has been processed with eligibility status: {} and reason: {}", result.getEntity().getId(), result.getEntity().getRuleExpression(), result.getStatus(), result.getReason());
    }

}
