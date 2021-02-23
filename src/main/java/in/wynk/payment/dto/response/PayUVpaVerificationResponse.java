package in.wynk.payment.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
public class PayUVpaVerificationResponse {

    private String status;
    private String vpa;
    private int isVPAValid;
    private String payerAccountName;
    @Setter
    private boolean isValid;
}
