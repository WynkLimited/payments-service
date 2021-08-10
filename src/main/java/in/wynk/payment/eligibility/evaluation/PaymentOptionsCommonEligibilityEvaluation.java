package in.wynk.payment.eligibility.evaluation;

import in.wynk.data.entity.MongoBaseEntity;
import in.wynk.eligibility.constant.EligibilityConstant;
import in.wynk.eligibility.dto.AbstractEligibilityEvaluation;
import in.wynk.eligibility.dto.EligibilityResult;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import in.wynk.spel.IRuleEvaluator;
import in.wynk.spel.builder.DefaultStandardExpressionContextBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Objects;

@Slf4j
@Getter
@SuperBuilder
@RequiredArgsConstructor
public abstract class PaymentOptionsCommonEligibilityEvaluation<T extends MongoBaseEntity<String>, P extends PaymentOptionsEligibilityRequest> extends AbstractEligibilityEvaluation<T, P> {

    private final IRuleEvaluator evaluator;

    @Override
    public EligibilityResult<T> evaluate(AbstractEligibilityEvaluation<T, P> rule) {
        final String expression = EligibilityConstant.PRE_ELIGIBILITY_CONDITION + rule.getExpression() + EligibilityConstant.POST_ELIGIBILITY_CONDITION;
        try {
            final StandardEvaluationContext evaluationContext = DefaultStandardExpressionContextBuilder.builder().rootObject(rule).build();
            final boolean val = evaluator.evaluate(expression, () -> evaluationContext, Boolean.class);
            EligibilityResult eligibilityResult = rule.getResult();
            if(Objects.isNull(eligibilityResult)) {
                if (val) {
                    eligibilityResult = EligibilityResult.<T>builder().entity(rule.getEntity()).status(EligibilityStatus.ELIGIBLE).build();
                } else {
                    eligibilityResult = EligibilityResult.<T>builder().entity(rule.getEntity()).status(EligibilityStatus.NOT_ELIGIBLE).build();
                }
            } else {
                if (!val && eligibilityResult.isEligible()) {
                    eligibilityResult = EligibilityResult.<T>builder().entity(rule.getEntity()).status(EligibilityStatus.NOT_ELIGIBLE).build();
                }
            }
            onEligibilityResult(eligibilityResult);
            return eligibilityResult;
        } catch (Exception e) {
            throw new WynkRuntimeException(EligibilityErrorType.EL001, e, "unable to evaluate eligibility for entity: " + rule.getEntity().toString() + " with expression: " + rule.getExpression());
        }
    }

    protected void onEligibilityResult(EligibilityResult<T> result) { }
}
