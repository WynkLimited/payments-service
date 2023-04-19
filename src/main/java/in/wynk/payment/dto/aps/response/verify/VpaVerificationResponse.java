package in.wynk.payment.dto.aps.response.verify;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@NoArgsConstructor
public class VpaVerificationResponse {
    private String status;
    private ResponseStatus responseStatus;
    private String vpa;
    private String payeeAccountName;
    private boolean vpaValid;
    private boolean isAutoPayHandleValid;

    @Getter
    @ToString
    @NoArgsConstructor
    public class ResponseStatus {
        private String code;
        private String pgStatus;
        private String description;
        private String httpStatus;
    }
}
