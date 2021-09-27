package in.wynk.payment.dto.response.addtobill;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AddToBillEligibilityAndPricingResponse  {
    private final EligibilityResponseBody body;
    private final EligibilityErrors errors;
    private final boolean success;



    @Getter
    @Builder
    public static class EligibilityErrors {
        private String code;
        private String field;
        private String message;

    }

}
