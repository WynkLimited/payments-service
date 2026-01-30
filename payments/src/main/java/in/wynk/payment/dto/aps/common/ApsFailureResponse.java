package in.wynk.payment.dto.aps.common;

import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * @author Nishesh Pandey
 */
@Getter
@NoArgsConstructor
public class ApsFailureResponse {
    private boolean result;
    private String requestId;
    private String errorCode;
    private String  errorMessage;
    private String message;
    private String statusCode;

    public void setStatusCode (String statusCode) {
        this.statusCode = statusCode;
    }
}
