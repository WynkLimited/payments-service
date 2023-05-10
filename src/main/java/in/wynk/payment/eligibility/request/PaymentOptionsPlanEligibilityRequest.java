package in.wynk.payment.eligibility.request;

import in.wynk.common.dto.SessionDTO;
import in.wynk.session.context.SessionContextHolder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import static in.wynk.common.constant.BaseConstants.CLIENT;

@Getter
@SuperBuilder
public class PaymentOptionsPlanEligibilityRequest extends PaymentOptionsEligibilityRequest {
    private final String planId;
    public String getClient() {
        return SessionContextHolder.<SessionDTO>getBody().get(CLIENT);
    }
}
