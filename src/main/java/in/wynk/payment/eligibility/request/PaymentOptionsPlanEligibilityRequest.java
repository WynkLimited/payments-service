package in.wynk.payment.eligibility.request;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class PaymentOptionsPlanEligibilityRequest extends PaymentOptionsEligibilityRequest {
    private final String planId;
}
