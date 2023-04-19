package in.wynk.payment.dto.common;

import in.wynk.payment.dto.response.PaymentOptionsDTO;
import in.wynk.payment.eligibility.request.PaymentOptionsEligibilityRequest;
import lombok.Builder;
import lombok.Getter;

import java.util.List;


@Getter
@Builder
public class FilteredPaymentOptionsResult {

    private boolean trialEligible;
    private final List<PaymentOptionsDTO.PaymentMethodDTO> methods;
    private final PaymentOptionsEligibilityRequest eligibilityRequest;
}
