package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentOption;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingRequest {

    private final String sessionId;
    private final String partnerProductId;
    private final String couponId;
    private final PaymentOption paymentOption;
    private final String enforcePayment; // Do we have to store this value at backend in configuration?
    private final String paymentGroup;
    private final String msisdn;
    private final String transactionId;
}
