package in.wynk.payment.dto.response.payu;

import lombok.Getter;

@Getter
public class PayUMandateUpiStatusResponse {

    private String status;
    private String action;
    private String amount;
    private String authpayuid;
    private String mandateEndDate;
    private String mandateStartDate;

}
