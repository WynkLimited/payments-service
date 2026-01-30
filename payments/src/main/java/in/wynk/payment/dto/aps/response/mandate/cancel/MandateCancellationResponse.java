package in.wynk.payment.dto.aps.response.mandate.cancel;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

/**
 * @author Nishesh Pandey
 */
@Getter
@SuperBuilder
@NoArgsConstructor
public class MandateCancellationResponse {
    private String cancellationRequestId;
    private String mandateTransactionId;
    private AutopayStatus autopayStatus;
    @Getter
    @SuperBuilder
    @NoArgsConstructor
    public static class AutopayStatus {
        private String siRegistrationStatus;
    }

}
