package in.wynk.payment.dto.response.payu;

import lombok.Getter;
import lombok.Setter;

@Getter
public class PayUVpaVerificationResponse {

    private String vpa;
    private String status;
    private int isVPAValid;
    private String payerAccountName;
    @Setter
    private boolean isValid;

}
