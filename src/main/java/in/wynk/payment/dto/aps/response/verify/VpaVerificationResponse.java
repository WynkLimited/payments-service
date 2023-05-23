package in.wynk.payment.dto.aps.response.verify;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Getter
@ToString
@NoArgsConstructor
public class VpaVerificationResponse {
    private String status;
    private ResponseStatus responseStatus;
    private String vpa;
    private String payeeAccountName;
    private boolean vpaValid;
    @JsonProperty("isAutoPayHandleValid")
    private boolean autoPayHandleValid;

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
