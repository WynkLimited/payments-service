package in.wynk.payment.dto.aps.response.bin;

import lombok.Getter;
import lombok.ToString;

@Getter
@ToString
public class ApsVpaVerificationResponse {

    private String vpa;
    private String status;
    private boolean vpaValid;
    private String payeeAccountName;
    private ResponseStatus responseStatus;


    private class ResponseStatus {
        private String code;
        private String pgStatus;
        private String description;
    }

}
