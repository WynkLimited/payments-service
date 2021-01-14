package in.wynk.payment.dto.response.payu;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PayUMandateUpiStatusResponse {

    private String status;
    private String action;
    private String amount;
    private String authpayuid;
    private String mandateEndDate;
    private String mandateStartDate;

}
