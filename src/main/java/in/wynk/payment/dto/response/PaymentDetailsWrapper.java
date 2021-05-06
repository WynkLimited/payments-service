package in.wynk.payment.dto.response;

import lombok.Getter;

import java.util.HashMap;
import java.util.Map;

@Getter
public class PaymentDetailsWrapper {
    private final Map<String, Map<String, AbstractPaymentDetails>> details = new HashMap<>();
}