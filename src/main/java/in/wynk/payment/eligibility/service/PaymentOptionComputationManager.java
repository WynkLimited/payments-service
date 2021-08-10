package in.wynk.payment.eligibility.service;

import in.wynk.eligibility.service.AbstractEligibilityService;
import in.wynk.payment.dto.response.PaymentOptionsComputationResponse;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsItemEligibilityRequest;
import in.wynk.payment.eligibility.request.PaymentOptionsPlanEligibilityRequest;
import in.wynk.spel.IRuleEvaluator;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@SuperBuilder
public class PaymentOptionComputationManager<R extends PaymentOptionsComputationResponse, T extends PaymentOptionsEligibilityRequest> extends AbstractEligibilityService<R,T> implements IPaymentOptionComputationManager<R,T>{

    private final IRuleEvaluator evaluator;

    public PaymentOptionComputationManager(IRuleEvaluator evaluator) {
        super(evaluator);
        this.evaluator =evaluator;
    }

    @Override
    public R compute(T request) {
        return (R) (request instanceof PaymentOptionsPlanEligibilityRequest ? compute((PaymentOptionsPlanEligibilityRequest) request) : compute((PaymentOptionsItemEligibilityRequest) request));
    }

    private PaymentOptionsComputationResponse compute(PaymentOptionsPlanEligibilityRequest request) {
        return PaymentOptionsComputationResponse.builder().build();
    }

    private PaymentOptionsComputationResponse compute(PaymentOptionsItemEligibilityRequest request) {
        return PaymentOptionsComputationResponse.builder().build();
    }

}
