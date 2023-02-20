package in.wynk.payment.dto.aps.response.verify;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@ToString
@Builder
public class ApsVpaVerificationData {
    private String status;
    private ResponseStatus responseStatus;

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
