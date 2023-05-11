package in.wynk.payment.eligibility.request;

import in.wynk.common.dto.SessionDTO;
import in.wynk.session.context.SessionContextHolder;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

import static in.wynk.common.constant.BaseConstants.CLIENT;

@Getter
@SuperBuilder
public class PaymentOptionsItemEligibilityRequest extends PaymentOptionsEligibilityRequest {
    private final String itemId;
    //TODO: When class for S2S is written get client from session for Web request and load client in S2S endpoint
    public String getClient () {
        return SessionContextHolder.<SessionDTO>getBody().get(CLIENT);
    }
}
