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

    private String sessionId;
    private String partnerProductId;
    private String couponId;
    private PaymentOption paymentOption;
    private String enforcePayment; // Do we have to store this value at backend in configuration?
    private String pg;
    private String msisdn;
    private String transactionId;
    private String service;
}
