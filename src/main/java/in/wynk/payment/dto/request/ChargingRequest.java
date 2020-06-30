package in.wynk.payment.dto.request;

import in.wynk.payment.core.constant.PaymentCode;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChargingRequest {

    private int planId;
    private PaymentCode paymentCode;
    private String enforcePayment; // Do we have to store this value at backend in configuration?
    private String pg;
    private String msisdn;
}
