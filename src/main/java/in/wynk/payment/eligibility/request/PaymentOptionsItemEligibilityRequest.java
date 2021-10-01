package in.wynk.payment.eligibility.request;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class PaymentOptionsItemEligibilityRequest extends PaymentOptionsEligibilityRequest {
    private final String itemId;
}
