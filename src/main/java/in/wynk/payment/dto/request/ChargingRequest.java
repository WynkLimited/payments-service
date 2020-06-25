package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentOption;
import lombok.*;

@Data
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
