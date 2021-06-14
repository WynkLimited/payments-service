package in.wynk.payment.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class PaymentDetailsWrapper {
    private Map<String, Map<String, AbstractPaymentDetails>> details;
}