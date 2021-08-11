package in.wynk.payment.eligibility.evaluation;

import in.wynk.data.entity.MongoBaseEntity;
import in.wynk.eligibility.dto.AbstractEligibilityEvaluation;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Getter
@SuperBuilder
public abstract class PaymentOptionsCommonEligibilityEvaluation<T extends MongoBaseEntity<String>, P extends PaymentOptionsEligibilityRequest> extends AbstractEligibilityEvaluation<T,P> {

}
